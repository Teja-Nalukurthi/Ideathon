"""
insect_sidecar/main.py
Flask server providing live insect detection as an entropy source.

Endpoints:
  GET /insect/reading   — Latest insect coordinates (JSON)
  GET /insect/stream    — MJPEG webcam feed (embed via <img> tag)
  GET /cameras          — List available camera indices + resolution
  POST /camera/select   — Switch active camera at runtime {"index": N}
  GET /health           — Status check

CLI:
  python main.py --camera 1   (default: 0)
  python main.py --list-cameras

Called by Spring's EntropyPool via HTTP every transaction.
Polled by EntropyController SSE loop every 500ms.
"""

import time
import threading
import argparse
import numpy as np
import cv2
from flask import Flask, jsonify, Response, request

app = Flask(__name__)

#  Global state (updated by background thread) 
state = {
    "insects": [],
    "count": 0,
    "captured_at_ns": 0,
    "frame_id": "0",
    "simulation": False,
    "camera_index": 0,
}
latest_jpeg = None
state_lock = threading.Lock()

#  Camera control 
active_camera_index = 0
camera_lock = threading.Lock()
camera_switch_event = threading.Event()

#  Load YOLOv8 
yolo_model = None
try:
    from ultralytics import YOLO
    yolo_model = YOLO("yolov8n.pt")
    print("YOLOv8n model loaded.")
except Exception as e:
    print(f"YOLOv8 unavailable ({e}). Using motion detection only.")


def probe_cameras(max_index: int = 6) -> list:
    """Try opening cameras 0..max_index-1 and return available ones with resolution."""
    available = []
    for i in range(max_index):
        cap = cv2.VideoCapture(i)
        if cap.isOpened():
            w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            available.append({"index": i, "width": w, "height": h})
            cap.release()
    return available


def capture_loop(camera_index: int) -> bool:
    """
    Runs the detection loop for one camera.
    Returns True  when camera_switch_event fires (caller should restart with new index).
    Returns False when the camera could not be opened (caller falls back to simulation).
    """
    global latest_jpeg

    cap = cv2.VideoCapture(camera_index)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
    cap.set(cv2.CAP_PROP_FPS, 10)

    if not cap.isOpened():
        cap.release()
        print(f"Camera {camera_index} not available.")
        return False

    print(f"Camera {camera_index} opened. Starting detection loop...")
    prev_gray = None
    frame_num = 0

    while not camera_switch_event.is_set():
        ok, frame = cap.read()
        if not ok:
            time.sleep(0.05)
            continue

        frame_num += 1
        insects = []
        h, w = frame.shape[:2]

        #  Motion detection 
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

        #  YOLOv8 (every 5th frame) 
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

        #  HUD overlay 
        cv2.rectangle(frame, (0, 0), (w, 32), (10, 14, 20), -1)
        cv2.putText(frame,
                    f"NIDHI ENTROPY | Cam: {camera_index} | Entities: {len(insects)} | Frame: {frame_num}",
                    (8, 22), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 230, 120), 1)

        #  Update shared state 
        with state_lock:
            state.update({
                "insects": insects[:20],
                "count": len(insects),
                "captured_at_ns": time.time_ns(),
                "frame_id": str(frame_num),
                "simulation": False,
                "camera_index": camera_index,
            })
            _, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 72])
            latest_jpeg = buf.tobytes()

        prev_gray = gray
        time.sleep(0.1)

    cap.release()
    print(f"Camera {camera_index} released.")
    return True  # switched


def webcam_loop():
    """Supervisor: starts capture_loop, restarts it whenever camera_switch_event fires."""
    global active_camera_index

    while True:
        with camera_lock:
            idx = active_camera_index
        camera_switch_event.clear()

        ok = capture_loop(idx)
        if not ok:
            # Camera unavailable — run simulation until /camera/select is called
            print("No camera available. Running simulation mode.")
            print("  POST /camera/select {\"index\": N}  to switch to a real camera.")
            sim_thread = threading.Thread(target=simulation_loop, daemon=True)
            sim_thread.start()
            camera_switch_event.wait()  # block until a camera is chosen via API


def simulation_loop():
    """Generates synthetic insect data. Exits when camera_switch_event is set."""
    frame_num = 0
    while not camera_switch_event.is_set():
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
                "simulation": True,
                "camera_index": -1,
            })

        time.sleep(0.1)


#  Flask routes 

@app.route("/cameras")
def list_cameras():
    """Return all available camera indices with their resolution."""
    available = probe_cameras()
    with camera_lock:
        current = active_camera_index
    with state_lock:
        sim = state["simulation"]
    return jsonify({
        "available": available,
        "current": current,
        "simulationMode": sim,
    })


@app.route("/camera/select", methods=["POST"])
def select_camera():
    """Switch to a different camera at runtime. Body: {"index": N}"""
    global active_camera_index
    data = request.get_json(force=True, silent=True) or {}
    idx = data.get("index")
    if idx is None:
        return jsonify({"error": "'index' field required"}), 400
    try:
        idx = int(idx)
    except (TypeError, ValueError):
        return jsonify({"error": "'index' must be an integer"}), 400

    # Verify the camera actually opens before committing
    test = cv2.VideoCapture(idx)
    if not test.isOpened():
        test.release()
        return jsonify({"error": f"Camera {idx} is not available"}), 404
    test.release()

    with camera_lock:
        active_camera_index = idx
    camera_switch_event.set()
    print(f"Camera switched to {idx} via API.")
    return jsonify({"ok": True, "selected": idx})


@app.route("/insect/reading")
def reading():
    with state_lock:
        s = dict(state)
    return jsonify({
        "insects": s["insects"],
        "count": s["count"],
        "capturedAtNs": s["captured_at_ns"],
        "frameId": s["frame_id"],
        "cameraIndex": s["camera_index"],
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
        cam_idx = state["camera_index"]
    with camera_lock:
        active = active_camera_index
    return jsonify({
        "status": "UP",
        "insectCount": count,
        "webcamOnline": not sim,
        "simulationMode": sim,
        "activeCameraIndex": active,
        "capturingCameraIndex": cam_idx,
    })


#  Start 

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Nidhi insect entropy sidecar")
    parser.add_argument("--camera", type=int, default=0,
                        help="Camera index to start with (default: 0)")
    parser.add_argument("--list-cameras", action="store_true",
                        help="Print available cameras and exit")
    args = parser.parse_args()

    if args.list_cameras:
        cams = probe_cameras()
        if cams:
            print("Available cameras:")
            for c in cams:
                print(f"  [{c['index']}]  {c['width']}x{c['height']}")
        else:
            print("No cameras found.")
        raise SystemExit(0)

    active_camera_index = args.camera

    t = threading.Thread(target=webcam_loop, daemon=True)
    t.start()
    time.sleep(1.0)  # Let first frame capture

    cams = probe_cameras()
    print("Insect entropy sidecar ready.")
    print(f"  Starting on camera: {active_camera_index}")
    if cams:
        print("  Available cameras: " + ", ".join(
            f"[{c['index']}] {c['width']}x{c['height']}" for c in cams))
    print("  /insect/reading       — coordinate API")
    print("  /insect/stream        — MJPEG feed")
    print("  /cameras              — list cameras")
    print('  /camera/select  POST  — switch camera  {"index": N}')
    print("  /health               — status")
    app.run(host="0.0.0.0", port=5001, debug=False, threaded=True)
