Deterministic 64 × 64 fixtures generated locally with Pillow 12.3.0.
The APNG, GIF and lossless WebP each contain three full-canvas frames: #e53935,
#1e88e5 and #43a047, at 240 ms per frame, looping indefinitely. static.png is
the first frame. APNG stays in assets so Android's drawable processing cannot
flatten it. These fixtures contain no downloaded user images.

three-frame.avif is locally converted from three-frame.gif with FFmpeg/libaom AV1
(CRF 20). It preserves the same three colors and durations to verify AVIF frame
playback instead of merely decoding its static cover.
