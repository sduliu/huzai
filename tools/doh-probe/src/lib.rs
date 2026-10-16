//! Opt-in Android test transport. Never installed into the application HTTP stack.
use base64::{engine::general_purpose::STANDARD, Engine};
use bytes::Bytes;
use hickory_proto::{
    op::{Message, MessageType, OpCode, Query, ResponseCode},
    rr::{rdata::svcb::SvcParamValue, DNSClass, Name, RData, RecordType},
    serialize::binary::{BinEncodable, BinEncoder},
};
use http_body_util::{BodyExt, Full};
use hyper::Request;
use hyper_util::rt::{TokioExecutor, TokioIo};
use jni::{
    objects::{JClass, JString},
    sys::jstring,
    JNIEnv,
};
use rustls::{
    client::{EchConfig, EchMode, EchStatus},
    pki_types::{EchConfigListBytes, ServerName},
    ClientConfig, RootCertStore,
};
use serde::Deserialize;
use serde_json::{json, Value};
use std::{
    net::{IpAddr, SocketAddr},
    sync::{
        atomic::{AtomicU16, Ordering},
        Arc,
    },
    time::{Duration, Instant},
};
use tokio::{
    net::{lookup_host, TcpStream},
    time::timeout,
};
use tokio_rustls::TlsConnector;
use url::Url;

const SERVER: &str = "https://edge.1molchuan.top/linuxdo";
const BODY_LIMIT: usize = 8 * 1024 * 1024;
static DNS_ID: AtomicU16 = AtomicU16::new(1024);
type Result<T> = std::result::Result<T, Failure>;

#[derive(Debug, Clone, Copy)]
struct Failure(&'static str, &'static str);

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Input {
    mode: String,
    #[serde(default)]
    url: String,
    #[serde(default)]
    cookie: String,
    #[serde(default)]
    user_agent: String,
    #[serde(default = "default_family")]
    family: String,
}
fn default_family() -> String {
    "any".into()
}

fn target(raw: &str) -> Result<Url> {
    let url = Url::parse(raw).map_err(|_| Failure("input", "invalid_url"))?;
    let path = url.path();
    if url.scheme() != "https"
        || url.host_str() != Some("linux.do")
        || url.port_or_known_default() != Some(443)
        || !url.username().is_empty()
        || url.password().is_some()
        || url.fragment().is_some()
        || !(path == "/session/current.json"
            || path == "/latest.json"
            || path.starts_with("/user_avatar/")
            || path.starts_with("/uploads/"))
    {
        return Err(Failure("input", "target_not_allowed"));
    }
    Ok(url)
}

fn tls_config(ech: Option<Vec<u8>>) -> Result<ClientConfig> {
    let provider = Arc::new(rustls::crypto::aws_lc_rs::default_provider());
    let mut roots = RootCertStore::empty();
    roots.extend(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());
    let builder = ClientConfig::builder_with_provider(provider);
    let builder = if let Some(bytes) = ech {
        let config = EchConfig::new(
            EchConfigListBytes::from(bytes),
            rustls::crypto::aws_lc_rs::hpke::ALL_SUPPORTED_SUITES,
        )
        .map_err(|_| Failure("ech_config", "invalid_or_unsupported"))?;
        builder
            .with_ech(EchMode::Enable(config))
            .map_err(|_| Failure("ech_config", "unsupported"))?
    } else {
        builder
            .with_safe_default_protocol_versions()
            .map_err(|_| Failure("tls", "unsupported_version"))?
    };
    let mut config = builder.with_root_certificates(roots).with_no_client_auth();
    config.alpn_protocols = vec![b"h2".to_vec(), b"http/1.1".to_vec()];
    Ok(config)
}

struct Reply {
    status: u16,
    content_type: String,
    challenge: String,
    retry_after: String,
    body: Vec<u8>,
    ech: EchStatus,
    alpn: String,
    ipv6: bool,
}

async fn read_body<B: hyper::body::Body<Data = Bytes> + Unpin>(
    mut body: B,
    limit: usize,
) -> Result<Vec<u8>> {
    let mut output = Vec::new();
    while let Some(frame) = body.frame().await {
        let frame = frame.map_err(|_| Failure("http_body", "read_failed"))?;
        if let Ok(data) = frame.into_data() {
            if output.len().saturating_add(data.len()) > limit {
                return Err(Failure("http_body", "too_large"));
            }
            output.extend_from_slice(&data);
        }
    }
    Ok(output)
}

fn request_uri(url: &Url, alpn: &str) -> String {
    if alpn == "h2" {
        return url.as_str().to_owned();
    }
    let mut path = url.path().to_owned();
    if let Some(query) = url.query() {
        path.push('?');
        path.push_str(query);
    }
    path
}

async fn exchange(
    url: &Url,
    addresses: Vec<IpAddr>,
    config: ClientConfig,
    body: Option<Vec<u8>>,
    ua: &str,
    cookie: &str,
    limit: usize,
) -> Result<Reply> {
    let host = url.host_str().ok_or(Failure("input", "missing_host"))?;
    let connector = TlsConnector::from(Arc::new(config));
    let server_name =
        ServerName::try_from(host.to_owned()).map_err(|_| Failure("input", "invalid_host"))?;
    let mut connected = None;
    let mut tls_failed = false;
    // Retry connection establishment only: no HTTP request has been sent yet.
    for ip in addresses.into_iter().take(8) {
        let tcp = match timeout(
            Duration::from_secs(3),
            TcpStream::connect(SocketAddr::new(ip, 443)),
        )
        .await
        {
            Ok(Ok(tcp)) => tcp,
            _ => continue,
        };
        match timeout(
            Duration::from_secs(5),
            connector.connect(server_name.clone(), tcp),
        )
        .await
        {
            Ok(Ok(tls)) => {
                connected = Some((tls, ip.is_ipv6()));
                break;
            }
            _ => {
                tls_failed = true;
            }
        }
    }
    let (tls, ipv6) = connected.ok_or(if tls_failed {
        Failure("tls", "handshake_or_certificate_failed")
    } else {
        Failure("tcp", "connection_failed")
    })?;
    let connection = tls.get_ref().1;
    let ech = connection.ech_status();
    let alpn =
        String::from_utf8_lossy(connection.alpn_protocol().unwrap_or(b"http/1.1")).into_owned();
    let is_post = body.is_some();
    let mut builder = Request::builder()
        .method(if is_post { "POST" } else { "GET" })
        .uri(request_uri(url, &alpn))
        .header("Host", host)
        .header("User-Agent", ua)
        .header("Accept-Encoding", "identity");
    if is_post {
        if !cookie.is_empty() {
            return Err(Failure("input", "dns_credentials_forbidden"));
        }
        builder = builder
            .header("Content-Type", "application/dns-message")
            .header("Accept", "application/dns-message");
    } else {
        builder = builder
            .header(
                "Accept",
                if url.path().ends_with(".json") {
                    "application/json"
                } else {
                    "image/*"
                },
            )
            .header("Referer", "https://linux.do/")
            .header("X-Requested-With", "XMLHttpRequest");
        if !cookie.is_empty() {
            builder = builder.header("Cookie", cookie);
        }
    }
    let request = builder
        .body(Full::new(Bytes::from(body.unwrap_or_default())))
        .map_err(|_| Failure("input", "invalid_headers"))?;
    let response = if alpn == "h2" {
        let (mut sender, connection) =
            hyper::client::conn::http2::handshake(TokioExecutor::new(), TokioIo::new(tls))
                .await
                .map_err(|_| Failure("http", "h2_handshake_failed"))?;
        tokio::spawn(async move {
            let _ = connection.await;
        });
        sender
            .send_request(request)
            .await
            .map_err(|_| Failure("http", "request_failed"))?
    } else {
        let (mut sender, connection) = hyper::client::conn::http1::handshake(TokioIo::new(tls))
            .await
            .map_err(|_| Failure("http", "h1_handshake_failed"))?;
        tokio::spawn(async move {
            let _ = connection.await;
        });
        sender
            .send_request(request)
            .await
            .map_err(|_| Failure("http", "request_failed"))?
    };
    let header = |name: &str| {
        response
            .headers()
            .get(name)
            .and_then(|v| v.to_str().ok())
            .unwrap_or("")
            .to_owned()
    };
    let mut reply = Reply {
        status: response.status().as_u16(),
        content_type: header("content-type"),
        challenge: header("cf-mitigated"),
        retry_after: header("retry-after"),
        body: Vec::new(),
        ech,
        alpn,
        ipv6,
    };
    reply.body = read_body(response.into_body(), limit).await?;
    Ok(reply)
}

fn dns_query(kind: RecordType) -> Result<Message> {
    let mut message = Message::new();
    message
        .set_id(DNS_ID.fetch_add(1, Ordering::Relaxed))
        .set_recursion_desired(true)
        .add_query(Query::query(
            Name::from_ascii("linux.do.").map_err(|_| Failure("dns", "invalid_name"))?,
            kind,
        ));
    Ok(message)
}

fn dns_response(query: &Message, body: &[u8]) -> Result<Message> {
    let reply = Message::from_vec(body).map_err(|_| Failure("dns", "malformed_reply"))?;
    if reply.id() != query.id()
        || reply.message_type() != MessageType::Response
        || reply.op_code() != OpCode::Query
        || reply.truncated()
        || reply.queries() != query.queries()
        || reply.response_code() != ResponseCode::NoError
    {
        return Err(Failure("dns", "mismatched_or_failed_reply"));
    }
    Ok(reply)
}

async fn query(kind: RecordType, server: &Url, bootstrap: &[IpAddr]) -> Result<Message> {
    let request = dns_query(kind)?;
    let bytes = request
        .to_vec()
        .map_err(|_| Failure("dns", "encode_failed"))?;
    let reply = exchange(
        server,
        bootstrap.to_vec(),
        tls_config(None)?,
        Some(bytes),
        "Kudo-DoH-Probe/0.1",
        "",
        65535,
    )
    .await?;
    if reply.status != 200
        || !reply
            .content_type
            .split(';')
            .next()
            .unwrap_or("")
            .eq_ignore_ascii_case("application/dns-message")
    {
        return Err(Failure("doh", "http_status_or_content_type"));
    }
    dns_response(&request, &reply.body)
}

fn extract(messages: &[Message]) -> Result<(Vec<IpAddr>, Vec<u8>, u32)> {
    let name = Name::from_ascii("linux.do.").map_err(|_| Failure("dns", "invalid_name"))?;
    let mut addresses = Vec::new();
    let mut ech = Vec::new();
    let mut ttl = u32::MAX;
    for record in messages.iter().flat_map(|m| m.answers()) {
        if record.name() != &name || record.dns_class() != DNSClass::IN {
            continue;
        }
        match record.data() {
            Some(RData::A(value)) => {
                addresses.push(IpAddr::V4(value.0));
                ttl = ttl.min(record.ttl());
            }
            Some(RData::AAAA(value)) => {
                addresses.push(IpAddr::V6(value.0));
                ttl = ttl.min(record.ttl());
            }
            Some(RData::HTTPS(value)) if value.svc_priority() > 0 => {
                for (_, param) in value.svc_params() {
                    if let SvcParamValue::EchConfig(config) = param {
                        if ech.is_empty() {
                            // Hickory removes ECHConfigList's u16 length while decoding.
                            // Re-encode the value so rustls receives the complete TLS vector.
                            config
                                .emit(&mut BinEncoder::new(&mut ech))
                                .map_err(|_| Failure("ech_config", "encode_failed"))?;
                            ttl = ttl.min(record.ttl());
                        }
                    }
                }
            }
            _ => {}
        }
    }
    addresses.dedup();
    if addresses.is_empty() {
        return Err(Failure("dns", "no_addresses"));
    }
    Ok((addresses, ech, ttl))
}

async fn probe(input: Input) -> Result<Value> {
    if !["dns", "ech", "tls"].contains(&input.mode.as_str())
        || !["any", "ipv4", "ipv6"].contains(&input.family.as_str())
    {
        return Err(Failure("input", "invalid_mode_or_family"));
    }
    // Validate before any network access, including DNS.
    let url = if input.mode == "dns" {
        if !input.cookie.is_empty() {
            return Err(Failure("input", "dns_credentials_forbidden"));
        }
        None
    } else {
        Some(target(&input.url)?)
    };
    let start = Instant::now();
    let server = Url::parse(SERVER).map_err(|_| Failure("input", "invalid_server"))?;
    let bootstrap: Vec<_> = lookup_host((server.host_str().unwrap_or(""), 443))
        .await
        .map_err(|_| Failure("bootstrap", "lookup_failed"))?
        .map(|s| s.ip())
        .collect();
    let (a, aaaa, https) = tokio::join!(
        query(RecordType::A, &server, &bootstrap),
        query(RecordType::AAAA, &server, &bootstrap),
        query(RecordType::HTTPS, &server, &bootstrap)
    );
    let dns_records = json!({"A":a.is_ok(),"AAAA":aaaa.is_ok(),"HTTPS":https.is_ok()});
    let mut messages = Vec::new();
    let mut failure = Failure("dns", "no_responses");
    for result in [a, aaaa, https] {
        match result {
            Ok(message) => messages.push(message),
            Err(error) => failure = error,
        }
    }
    if messages.is_empty() {
        return Err(failure);
    }
    let (mut addresses, ech_bytes, ttl) = extract(&messages)?;
    addresses.retain(|ip| input.family == "any" || (input.family == "ipv6") == ip.is_ipv6());
    if addresses.is_empty() {
        return Err(Failure("dns", "family_unavailable"));
    }
    let dns_ms = start.elapsed().as_millis();
    if input.mode == "dns" {
        return Ok(
            json!({"ok":true,"addresses":addresses.iter().map(ToString::to_string).collect::<Vec<_>>(),
            "echConfigBytes":ech_bytes.len(),"ttl":ttl,"dnsMillis":dns_ms,"dnsRecords":dns_records}),
        );
    }
    let ech = if input.mode == "ech" {
        if ech_bytes.is_empty() {
            return Err(Failure("ech_config", "missing"));
        }
        Some(ech_bytes)
    } else {
        None
    };
    let reply = exchange(
        &url.unwrap(),
        addresses,
        tls_config(ech)?,
        None,
        &input.user_agent,
        &input.cookie,
        BODY_LIMIT,
    )
    .await?;
    Ok(
        json!({"ok":true,"status":reply.status,"contentType":reply.content_type,
        "cfMitigated":reply.challenge,"retryAfter":reply.retry_after,"ech":format!("{:?}",reply.ech),
        "alpn":reply.alpn,"ipv6":reply.ipv6,"dnsMillis":dns_ms,"dnsRecords":dns_records,"elapsedMillis":start.elapsed().as_millis(),
        "bodyBase64":STANDARD.encode(reply.body)}),
    )
}

fn run(raw: &str) -> Value {
    let work = || -> Result<Value> {
        if raw.len() > 32768 {
            return Err(Failure("input", "too_large"));
        }
        let input = serde_json::from_str(raw).map_err(|_| Failure("input", "invalid_json"))?;
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_all()
            .build()
            .map_err(|_| Failure("runtime", "initialization_failed"))?;
        runtime.block_on(async {
            timeout(Duration::from_secs(45), probe(input))
                .await
                .map_err(|_| Failure("timeout", "overall_deadline"))?
        })
    };
    match work() {
        Ok(value) => value,
        Err(Failure(stage, error)) => json!({"ok":false,"stage":stage,"error":error}),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_kudo_android_DohEchNativeProbe_exchange(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let output = match env.get_string(&input) {
        Ok(raw) => std::panic::catch_unwind(|| run(&String::from(raw)))
            .unwrap_or_else(|_| json!({"ok":false,"stage":"runtime","error":"panic"})),
        Err(_) => json!({"ok":false,"stage":"input","error":"jni_string"}),
    };
    match env.new_string(output.to_string()) {
        Ok(value) => value.into_raw(),
        Err(_) => {
            let _ = env.throw_new(
                "java/lang/IllegalStateException",
                "DoH probe allocation failed",
            );
            std::ptr::null_mut()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use hickory_proto::rr::{
        rdata::{
            svcb::{EchConfig as DnsEch, SvcParamKey},
            A, HTTPS, SVCB,
        },
        Record,
    };

    fn reply(query: &Message) -> Message {
        let mut message = query.clone();
        message.set_message_type(MessageType::Response);
        message
    }
    #[test]
    fn rejects_non_whitelisted_or_insecure_targets() {
        for url in [
            "http://linux.do/latest.json",
            "https://linux.do.evil.test/latest.json",
            "https://linux.do@evil.test/latest.json",
            "https://linux.do:444/latest.json",
            "https://linux.do/session/current.json#fragment",
            "https://linux.do/session/logout",
            "https://linux.do/posts.json",
        ] {
            assert!(target(url).is_err(), "{url}");
        }
        assert!(target("https://linux.do/latest.json").is_ok());
        assert!(target("https://linux.do/user_avatar/linux.do/test/48/1.png").is_ok());
    }
    #[test]
    fn rejects_malformed_mismatched_or_failed_dns() {
        let query = dns_query(RecordType::A).unwrap();
        assert!(dns_response(&query, &[0, 1]).is_err());
        let mut wrong_id = reply(&query);
        wrong_id.set_id(query.id().wrapping_add(1));
        assert!(dns_response(&query, &wrong_id.to_vec().unwrap()).is_err());
        let mut failed = reply(&query);
        failed.set_response_code(ResponseCode::ServFail);
        assert!(dns_response(&query, &failed.to_vec().unwrap()).is_err());
        assert!(dns_response(&query, &query.to_vec().unwrap()).is_err());
        assert!(dns_response(&query, &reply(&query).to_vec().unwrap()).is_ok());
    }
    #[test]
    fn extracts_addresses_ech_and_minimum_ttl() {
        let query = dns_query(RecordType::A).unwrap();
        let mut message = reply(&query);
        let name = Name::from_ascii("linux.do.").unwrap();
        message.add_answer(Record::from_rdata(
            name.clone(),
            14,
            RData::A(A::new(192, 0, 2, 1)),
        ));
        message.add_answer(Record::from_rdata(
            name,
            10,
            RData::HTTPS(HTTPS(SVCB::new(
                1,
                Name::root(),
                vec![(
                    SvcParamKey::EchConfig,
                    SvcParamValue::EchConfig(DnsEch(vec![1, 2, 3])),
                )],
            ))),
        ));
        let (ips, ech, ttl) = extract(&[message]).unwrap();
        assert_eq!(ips, vec!["192.0.2.1".parse::<IpAddr>().unwrap()]);
        assert_eq!(ech, vec![0, 3, 1, 2, 3]);
        assert_eq!(ttl, 10);
    }
    #[test]
    fn missing_ech_is_distinct_from_dns_failure() {
        let mut message = reply(&dns_query(RecordType::A).unwrap());
        message.add_answer(Record::from_rdata(
            Name::from_ascii("linux.do.").unwrap(),
            20,
            RData::A(A::new(192, 0, 2, 1)),
        ));
        assert!(extract(&[message]).unwrap().1.is_empty());
        assert!(extract(&[]).is_err());
        assert!(tls_config(Some(vec![0, 1, 2])).is_err());
    }
    #[test]
    fn credential_and_target_validation_precede_network() {
        let dns = run(r#"{"mode":"dns","cookie":"fixture-secret"}"#);
        assert_eq!(dns["error"], "dns_credentials_forbidden");
        assert!(!dns.to_string().contains("fixture-secret"));
        let target = run(r#"{"mode":"ech","url":"https://evil.test/","cookie":"fixture-secret"}"#);
        assert_eq!(target["error"], "target_not_allowed");
        assert!(!target.to_string().contains("fixture-secret"));
        assert_eq!(run("not json")["error"], "invalid_json");
    }
    #[test]
    fn h1_direct_requests_use_origin_form_and_h2_keeps_authority() {
        let url = Url::parse("https://linux.do/latest.json?page=1").unwrap();
        assert_eq!(request_uri(&url, "http/1.1"), "/latest.json?page=1");
        assert_eq!(request_uri(&url, "h2"), url.as_str());
    }
    #[tokio::test(flavor = "current_thread")]
    async fn response_body_is_bounded_before_append() {
        assert!(read_body(Full::new(Bytes::from_static(b"12345")), 4)
            .await
            .is_err());
        assert_eq!(
            read_body(Full::new(Bytes::from_static(b"1234")), 4)
                .await
                .unwrap(),
            b"1234"
        );
    }

    #[test]
    fn dns_ech_preserves_the_tls_list_length_prefix() {
        // Public Cloudflare ECH configuration, not a credential; works offline after key rotation.
        let wire = STANDARD.decode("AEX+DQBBvgAgACAnc+9IO/WWQSMj7W2mLvaFlY3UbvRlOsLeK+qbQ6pgVwAEAAEAAQASY2xvdWRmbGFyZS1lY2guY29tAAA=").unwrap();
        let query = dns_query(RecordType::HTTPS).unwrap();
        let mut message = reply(&query);
        let name = Name::from_ascii("linux.do.").unwrap();
        message.add_answer(Record::from_rdata(
            name.clone(),
            30,
            RData::A(A::new(192, 0, 2, 1)),
        ));
        message.add_answer(Record::from_rdata(
            name,
            30,
            RData::HTTPS(HTTPS(SVCB::new(
                1,
                Name::root(),
                vec![(
                    SvcParamKey::EchConfig,
                    SvcParamValue::EchConfig(DnsEch(wire[2..].to_vec())),
                )],
            ))),
        ));
        let decoded = dns_response(&query, &message.to_vec().unwrap()).unwrap();
        let (_, bytes, _) = extract(&[decoded]).unwrap();
        assert_eq!(
            bytes, wire,
            "Hickory stores the list without its two-byte length; TLS requires the whole list"
        );
        assert!(tls_config(Some(bytes)).is_ok());
    }
}
