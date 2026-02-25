"""
insect_sidecar/main.py
Flask server providing live insect detection as an entropy source.

Endpoints:
  GET /insect/reading   — Latest insect coordinates (JSON)
  GET /insect/stream    — MJPEG webcam feed (embed via <img> tag)
  GET /health           — Status check

Called by Spring's EntropyPool via HTTP every transaction.
Polled by EntropyController SSE loop every 500ms.
"""

import time
import threading
import json
import numpy as np
import cv2
from flask import Flask, jsonify, Response

app = Flask(__name__)

# ── Global state (updated by background thread) ────────────────
state = {
    "insects": [],
    "count": 0,
    "captured_at_ns": 0,
    "frame_id": "0",
    "simulation": False
}
latest_jpeg = None
state_lock = threading.Lock()

# ── Load YOLOv8 ────────────────────────────────────────────────
yolo_model = None
try:
    from ultralytics import YOLO
    yolo_model = YOLO("yolov8n.pt")
    print("YOLOv8n model loaded.")
except Exception as e:
    print(f"YOLOv8 unavailable ({e}). Using motion detection only.")


def webcam_loop():
    """Main capture loop. Runs in background thread."""
    global latest_jpeg

    cap = cv2.VideoCapture(0)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
    cap.set(cv2.CAP_PROP_FPS, 10)

    if not cap.isOpened():
        print("No webcam detected. Starting simulation mode.")
        simulation_loop()
        return

    print("Webcam opened. Starting detection loop...")
    prev_gray = None
    frame_num = 0

    while True:
        ok, frame = cap.read()
        if not ok:
            time.sleep(0.05)
            continue

        frame_num += 1
        insects = []
        h, w = frame.shape[:2]

        # ── Motion detection ────────────────────────────────────
        gray = cv2.GaussianBlur(cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY), (5, 5), 0)

        if prev_gray is not None:
            diff = cv2.absdiff(prev_gray, gray)
            _, thresh = cv2.threshold(diff, 20, 255, cv2.THRESH_BINARY)
            contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

            for c in contours:
                area = cv2.contourArea(c)
                if area < 8 or area > 40000:
                    continue
                M = cv2.moments(c)
                if M["m00"] == 0:
                    continue
                cx, cy = M["m10"] / M["m00"], M["m01"] / M["m00"]
                insects.append({
                    "x": round(cx / w, 6),
                    "y": round(cy / h, 6),
                    "confidence": round(min(area / 800, 1.0), 4)
                })
                x, y, bw, bh = cv2.boundingRect(c)
                cv2.rectangle(frame, (x, y), (x + bw, y + bh), (0, 230, 120), 1)
                cv2.putText(frame, f"{cx/w:.2f},{cy/h:.2f}",
                            (x, y - 3), cv2.FONT_HERSHEY_SIMPLEX, 0.3, (0, 230, 120), 1)

        # ── YOLOv8 (every 5th frame) ────────────────────────────
        if yolo_model is not None and frame_num % 5 == 0:
            try:
                results = yolo_model(frame, verbose=False, conf=0.25)
                for r in results:
                    for box in r.boxes:
                        x1, y1, x2, y2 = box.xyxy[0].tolist()
                        insects.append({
                            "x": round((x1 + x2) / 2 / w, 6),
                            "y": round((y1 + y2) / 2 / h, 6),
                            "confidence": round(float(box.conf[0]), 4)
                        })
                        cv2.rectangle(frame, (int(x1), int(y1)), (int(x2), int(y2)),
                                      (255, 140, 0), 2)
            except Exception:
                pass

        # ── HUD overlay ──────────────────────────────────────────
        cv2.rectangle(frame, (0, 0), (w, 32), (10, 14, 20), -1)
        cv2.putText(frame, f"NIDHI ENTROPY | Entities: {len(insects)} | Frame: {frame_num}",
                    (8, 22), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 230, 120), 1)

        # ── Update shared state ───────────────────────────────────
        with state_lock:
            state.update({
                "insects": insects[:20],
                "count": len(insects),
                "captured_at_ns": time.time_ns(),
                "frame_id": str(frame_num),
                "simulation": False
            })
            _, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 72])
            latest_jpeg = buf.tobytes()

        prev_gray = gray
        time.sleep(0.1)

    cap.release()


def simulation_loop():
    """Generates synthetic insect data when webcam is unavailable."""
    frame_num = 0
    while True:
        frame_num += 1
        n = np.random.randint(3, 9)
        insects = [{
            "x": round(float(np.random.random()), 6),
            "y": round(float(np.random.random()), 6),
            "confidence": round(float(np.random.uniform(0.5, 1.0)), 4)
        } for _ in range(n)]

        with state_lock:
            state.update({
                "insects": insects,
                "count": n,
                "captured_at_ns": time.time_ns(),
                "frame_id": f"sim-{frame_num}",
                "simulation": True
            })

        time.sleep(0.1)


# ── Flask routes ───────────────────────────────────────────────

@app.route("/insect/reading")
def reading():
    with state_lock:
        s = dict(state)
    return jsonify({
        "insects": s["insects"],
        "count": s["count"],
        "capturedAtNs": s["captured_at_ns"],
        "frameId": s["frame_id"]
    })


@app.route("/insect/stream")
def stream():
    def gen():
        while True:
            with state_lock:
                frame = latest_jpeg
            if frame:
                yield (b"--frame\r\n"
                       b"Content-Type: image/jpeg\r\n\r\n" + frame + b"\r\n")
            time.sleep(0.1)

    return Response(gen(), mimetype="multipart/x-mixed-replace; boundary=frame")


@app.route("/health")
def health():
    with state_lock:
        count = state["count"]
        sim = state["simulation"]
    return jsonify({
        "status": "UP",
        "insectCount": count,
        "webcamOnline": not sim,
        "simulationMode": sim
    })


# ── Start ──────────────────────────────────────────────────────

if __name__ == "__main__":
    t = threading.Thread(target=webcam_loop, daemon=True)
    t.start()
    time.sleep(1.0)  # Let first frame capture
    print("Insect entropy sidecar ready.")
    print("  /insect/reading  — coordinate API")
    print("  /insect/stream   — MJPEG feed")
    print("  /health          — status")
    app.run(host="0.0.0.0", port=5001, debug=False, threaded=True)
