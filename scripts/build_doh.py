"""Build the arm64 DNS/TLS bridge; all generated files stay under app/build."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--ndk", default="27.0.12077973")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    sdk = Path(args.sdk)
    ndk = Path(os.environ.get("KUDO_NDK_ROOT", sdk / "ndk" / args.ndk))
    host = "windows-x86_64" if sys.platform == "win32" else "darwin-x86_64" if sys.platform == "darwin" else "linux-x86_64"
    toolchain = ndk / "toolchains" / "llvm" / "prebuilt" / host / "bin"
    suffix = ".cmd" if sys.platform == "win32" else ""
    clang = toolchain / ("aarch64-linux-android26-clang" + suffix)
    if not clang.is_file() or shutil.which("cargo") is None:
        raise RuntimeError("Install Rust, rustup target add aarch64-linux-android, and Android NDK " + args.ndk)
    cmakes = sorted((sdk / "cmake").glob("*/bin/cmake*"))
    cmake_bin = cmakes[-1].parent if cmakes else None
    target = repo / "app/build/doh-native"
    output = repo / "app/build/generated/dohJni/arm64-v8a"
    target.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True, exist_ok=True)
    cmake_file = target / "android.cmake"
    content = ('set(ANDROID_ABI arm64-v8a CACHE STRING "" FORCE)\n'
               'set(ANDROID_PLATFORM android-26 CACHE STRING "" FORCE)\n'
               f'include("{(ndk / "build/cmake/android.toolchain.cmake").as_posix()}")\n')
    if not cmake_file.exists() or cmake_file.read_text(encoding="utf-8") != content:
        cmake_file.write_text(content, encoding="utf-8")
    env = os.environ.copy()
    env.update({
        "PATH": os.pathsep.join([str(cmake_bin)] if cmake_bin else []) + os.pathsep + str(toolchain) + os.pathsep + env.get("PATH", ""),
        "ANDROID_NDK_ROOT": str(ndk),
        "CMAKE_GENERATOR": "Ninja",
        "CMAKE_TOOLCHAIN_FILE_aarch64_linux_android": str(cmake_file),
        "CC_aarch64_linux_android": str(clang),
        "AR_aarch64_linux_android": str(toolchain / ("llvm-ar.exe" if sys.platform == "win32" else "llvm-ar")),
        "CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER": str(clang),
        "CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS": "-C link-arg=-Wl,-z,max-page-size=16384",
    })
    subprocess.run(["cargo", "build", "--locked", "--manifest-path", str(repo / "native/doh/Cargo.toml"),
                    "--target", "aarch64-linux-android", "--target-dir", str(target), "--release", "--lib"], env=env, check=True)
    subprocess.run([sys.executable, str(repo / "scripts/native_notices.py"), "--check"], check=True)
    library = target / "aarch64-linux-android/release/libkudo_doh.so"
    readelf = toolchain / ("llvm-readelf.exe" if sys.platform == "win32" else "llvm-readelf")
    segments = subprocess.check_output([str(readelf), "-lW", str(library)], text=True)
    loads = [line for line in segments.splitlines() if line.strip().startswith("LOAD ")]
    if not loads or any(line.split()[-1] != "0x4000" for line in loads):
        raise RuntimeError("DoH library must have 16KB ELF LOAD alignment")
    shutil.copyfile(library, output / library.name)
    print(f"DoH arm64: {library.stat().st_size} bytes; 16KB aligned")


if __name__ == "__main__":
    main()
