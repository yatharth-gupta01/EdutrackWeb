// --- AUTO INJECT CREDENTIALS ---
const originalFetch = window.fetch;
window.fetch = function() {
    let [resource, config] = arguments;
    if(config === undefined) {
        config = {};
    }
    config.credentials = 'include';
    return originalFetch(resource, config).then(res => {
        if (res.status === 401 && window.location.pathname !== '/' && resource !== '/login') {
            window.location.href = '/';
        }
        return res;
    });
};
// -------------------------------

function byId(id) {
    return document.getElementById(id);
}

// GUI Theme and Ripple Logic
function toggleTheme() {
    const body = document.body;
    body.classList.toggle('light-theme');
    const isLight = body.classList.contains('light-theme');
    localStorage.setItem('theme', isLight ? 'light' : 'dark');
    
    const icon = document.getElementById('themeIcon');
    if(icon) icon.className = isLight ? 'ph-bold ph-moon' : 'ph-bold ph-sun';
    
    const attendanceChartCanvas = document.getElementById('attendanceChart');
    if (attendanceChartCanvas && window.lastChartData) {
        renderAttendanceChart(window.lastChartData.present, window.lastChartData.absent);
        renderMarksChart(window.lastChartData.labels, window.lastChartData.scores);
    }
}

document.addEventListener("DOMContentLoaded", () => {
    if(localStorage.getItem('theme') === 'light') {
        document.body.classList.add('light-theme');
        const icon = document.getElementById('themeIcon');
        if(icon) icon.className = 'ph-bold ph-moon';
    }
    
    document.body.addEventListener('click', function(e) {
        let btn = e.target.closest('button');
        if(btn && !btn.classList.contains('theme-toggle')) {
            const rect = btn.getBoundingClientRect();
            let ripple = document.createElement('span');
            ripple.className = 'ripple';
            ripple.style.left = (e.clientX - rect.left) + 'px';
            ripple.style.top = (e.clientY - rect.top) + 'px';
            btn.appendChild(ripple);
            setTimeout(() => ripple.remove(), 600);
        }
    });
});

function setLoading(btn) {
    if(!btn) return;
    btn.disabled = true;
    btn.dataset.original = btn.innerHTML;
    btn.innerHTML = `<span class="spinner"></span> Wait...`;
}
function removeLoading(btn) {
    if(!btn) return;
    btn.disabled = false;
    if(btn.dataset.original) btn.innerHTML = btn.dataset.original;
}

function login(event) {
    const btn = event ? event.currentTarget || event.target : null;
    if(btn && btn.tagName === 'BUTTON') setLoading(btn);

    const userIdField = byId("id");
    const passwordField = byId("pass");
    const message = byId("msg");
    const userId = userIdField.value.trim();
    const password = passwordField.value.trim();

    if (!userId || !password) {
        message.innerText = "Enter user ID and password.";
        return;
    }

    fetch("/login", {
        method: "POST",
        body: userId + "," + password
    })
    .then(res => res.text())
    .then(authData => {
        const parts = authData.trim().split(",");
        const cleanRole = parts[0];
        const subject = parts.length > 1 ? parts[1] : "";

        if (cleanRole === "student" || cleanRole === "teacher" || cleanRole === "admin") {
            localStorage.setItem("userId", userId);
            localStorage.setItem("userRole", cleanRole);
            if (subject) localStorage.setItem("subject", subject);
            
            if (cleanRole === "admin") {
                window.location.href = "/admin";
            } else {
                window.location.href = "/dashboard";
            }
        } else {
            message.innerText = "Invalid login";
        }
    })
    .catch(() => {
        message.innerText = "Unable to reach server.";
    })
    .finally(() => {
        if(typeof btn !== 'undefined' && btn) removeLoading(btn);
    });
}

// --- SPA LOGIC ---
let attendanceChartInstance = null;
let marksChartInstance = null;

function showSection(sectionId) {
    document.querySelectorAll('.page-section').forEach(el => el.classList.remove('active'));
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
    
    const section = byId(sectionId);
    if (section) section.classList.add('active');
    
    const navItems = document.querySelectorAll('.nav-item');
    for(let item of navItems) {
        if(item.textContent.toLowerCase().includes(sectionId.toLowerCase()) || 
          (sectionId==='overview' && item.textContent==='Overview') ||
          (sectionId==='profile' && item.textContent==='Profile') ||
          (sectionId==='adminPanel' && item.textContent.includes('Admin'))) {
            item.classList.add('active');
        }
    }
}

function showToast(message, type = "info") {
    const container = byId("toastContainer");
    if (!container) return;
    const toast = document.createElement("div");
    toast.className = `toast ${type}`;
    toast.innerHTML = `<span>${message}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.animation = "slideOut 0.3s ease-in forwards";
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

function initDashboard() {
    const userId = localStorage.getItem("userId");
    const role = localStorage.getItem("userRole");
    const subject = localStorage.getItem("subject");

    if (!userId) {
        window.location.href = "/";
        return;
    }

    byId("greetingHeader").innerText = `Welcome back, ${userId}`;
    byId("roleBadge").innerText = role.toUpperCase();
    byId("userAvatar").innerText = userId.charAt(0).toUpperCase();

    // Profile Hydration
    byId("profileName").innerText = "User " + userId;
    byId("profileId").innerText = "ID: " + userId;
    byId("profileRole").innerText = role.toUpperCase();
    if(subject) byId("profileSubject").innerText = subject;
    byId("profileAvatar").innerText = userId.charAt(0).toUpperCase();

    if (role === "teacher") {
        document.querySelectorAll('.admin-teacher-tools').forEach(el => el.style.display = "block");
    }

    loadAttendanceData(userId, role);
    loadMarksData(userId, role);
}

async function loadAttendanceData(userId, role) {
    const targetId = role === "teacher" ? prompt("Enter Student ID to view their Attendance Analytics", userId) || userId : userId;
    
    try {
        const res = await fetch("/getAttendance?id=" + encodeURIComponent(targetId));
        const data = await res.text();
        const rows = parseCSV(data);

        const tbody = byId("attendanceTable").querySelector("tbody");
        tbody.innerHTML = "";
        
        let present = 0;
        let total = rows.length;

        rows.forEach(row => {
            if(row.length >= 3) {
                const tr = document.createElement("tr");
                const statusHtml = row[2].toLowerCase() === "present" ? `<span class="badge success">Present</span>` : `<span class="badge danger">Absent</span>`;
                tr.innerHTML = `<td>${escapeHtml(row[0])}</td><td>${escapeHtml(row[1])}</td><td>${statusHtml}</td>`;
                tbody.appendChild(tr);
                if (row[2].toLowerCase() === "present") present++;
            }
        });

        const pct = total === 0 ? 0 : Math.round((present / total) * 100);
        byId("cardAttendance").innerText = pct + "%";

        if (pct < 75 && total > 0) {
            showToast("Warning: Attendance is below 75% limit!", "warning");
        }

        window.lastChartData = window.lastChartData || {};
        window.lastChartData.present = present;
        window.lastChartData.absent = total - present;
        renderAttendanceChart(present, total - present);
    } catch(err) { console.error(err); }
}

async function loadMarksData(userId, role) {
    const targetId = role === "teacher" ? prompt("Enter Student ID to view their Marks Analytics", userId) || userId : userId;
    
    try {
        const res = await fetch("/getMarks?id=" + encodeURIComponent(targetId));
        const data = await res.text();
        const rows = parseCSV(data);

        const tbody = byId("marksTable").querySelector("tbody");
        tbody.innerHTML = "";
        
        let totalScore = 0;
        let labels = [];
        let scores = [];

        rows.forEach(row => {
            if(row.length >= 3) {
                const tr = document.createElement("tr");
                tr.innerHTML = `<td>${escapeHtml(row[0])}</td><td>${escapeHtml(row[1])}</td><td><strong>${escapeHtml(row[2])}</strong></td>`;
                tbody.appendChild(tr);
                
                labels.push(row[1]);
                let s = parseFloat(row[2]);
                if(!isNaN(s)) {
                    scores.push(s);
                    totalScore += s;
                }
            }
        });

        const avg = scores.length === 0 ? 0 : Math.round(totalScore / scores.length);
        byId("cardGrade").innerText = avg + "%";

        window.lastChartData = window.lastChartData || {};
        window.lastChartData.labels = labels;
        window.lastChartData.scores = scores;
        renderMarksChart(labels, scores);
    } catch(err) { console.error(err); }
}

function parseCSV(data) {
    return data.trim().split(/\r?\n/).filter(line => line.trim()!=="").map(line => line.split(",").map(i => i.trim()));
}

function renderAttendanceChart(present, absent) {
    const ctx = byId('attendanceChart').getContext('2d');
    if (attendanceChartInstance) attendanceChartInstance.destroy();
    
    const isLight = document.body.classList.contains('light-theme');
    const textColor = isLight ? '#475569' : '#f8fafc';
    
    const gradPresent = ctx.createLinearGradient(0, 0, 0, 300);
    gradPresent.addColorStop(0, '#34d399');
    gradPresent.addColorStop(1, '#059669');
    
    const gradAbsent = ctx.createLinearGradient(0, 0, 0, 300);
    gradAbsent.addColorStop(0, '#f87171');
    gradAbsent.addColorStop(1, '#dc2626');

    attendanceChartInstance = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: ['Present', 'Absent'],
            datasets: [{
                data: [present, absent],
                backgroundColor: [gradPresent, gradAbsent],
                borderWidth: 0,
                hoverOffset: 6
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'bottom', labels: { color: textColor, font: { family: 'Inter', size: 13 } } } },
            cutout: '70%',
            animation: { animateScale: true, animateRotate: true, duration: 1200 }
        }
    });
}

function renderMarksChart(labels, data) {
    const ctx = byId('marksChart').getContext('2d');
    if (marksChartInstance) marksChartInstance.destroy();
    
    const isLight = document.body.classList.contains('light-theme');
    const textColor = isLight ? '#475569' : '#94a3b8';
    const gridColor = isLight ? 'rgba(0,0,0,0.05)' : 'rgba(255,255,255,0.05)';
    
    const gradScore = ctx.createLinearGradient(0, 0, 0, 300);
    gradScore.addColorStop(0, '#818cf8');
    gradScore.addColorStop(1, '#4f46e5');

    marksChartInstance = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'Score',
                data: data,
                backgroundColor: gradScore,
                borderRadius: 6,
                barThickness: 'flex',
                maxBarThickness: 40
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                y: { 
                    ticks: { color: textColor, font: {family: 'Inter'} }, 
                    grid: { color: gridColor, drawBorder: false },
                    border: { display: false }
                },
                x: { 
                    ticks: { color: textColor, font: {family: 'Inter'} }, 
                    grid: { display: false },
                    border: { display: false }
                }
            },
            animation: { duration: 1200, easing: 'easeOutQuart' }
        }
    });
}

async function addAttendanceManually() {
    const id = byId("attStudentId").value;
    const sub = byId("attSubject").value;
    const status = byId("attStatus").value;
    const date = new Date().toISOString().split('T')[0];
    if(!id || !sub) return showToast("Fill all fields", "error");

    navigator.geolocation.getCurrentPosition(async pos => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        const res = await fetch("/addAttendance", { method: "POST", body: `${id},${sub},${date},${status},${lat},${lng},1.0`});
        showToast(await res.text(), "success");
        loadAttendanceData(localStorage.getItem("userId"), localStorage.getItem("userRole"));
    }, async err => {
        showToast("Geolocation failed: " + err.message + ". Attendance blocked.", "error");
    });
}

async function addMarksManually() {
    const id = byId("markStudentId").value;
    const ass = byId("markAssignment").value;
    const score = byId("markScore").value;
    const date = new Date().toISOString().split('T')[0];
    if(!id || !ass || !score) return showToast("Fill all fields", "error");

    const res = await fetch("/addMarks", { method: "POST", body: `${id},${date},${ass},${score}`});
    showToast(await res.text(), "success");
    loadMarksData(localStorage.getItem("userId"), localStorage.getItem("userRole"));
}

async function loadAllUsers() {
    try {
        const res = await fetch("/getAllUsers");
        const rows = parseCSV(await res.text());
        const tbody = byId("usersTable").querySelector("tbody");
        tbody.innerHTML = "";
        rows.forEach(row => {
            if(row.length >= 4) {
                const tr = document.createElement("tr");
                tr.innerHTML = `
                    <td><span class="badge ${row[0]==='teacher'?'info':'warning'}">${row[0]}</span></td>
                    <td>${escapeHtml(row[1])}</td>
                    <td>${escapeHtml(row[2])}</td>
                    <td>${escapeHtml(row.length>4?row[4]:'N/A')}</td>
                    <td><button class="danger" style="padding: 6px 12px; font-size:12px;" onclick="deleteUser('${row[1]}')">Delete</button></td>
                `;
                tbody.appendChild(tr);
            }
        });
    } catch(e){}
}

async function createNewUser() {
    const role = byId("newRole").value;
    const id = byId("newId").value;
    const name = byId("newName").value;
    const pass = byId("newPass").value;
    const sub = byId("newSub").value;
    if(!id || !name || !pass) return showToast("Missing critical user fields", "error");
    
    const res = await fetch("/addUser", { method: "POST", body: `${role},${id},${name},${pass},${sub}`});
    showToast(await res.text(), "success");
    loadAllUsers();
}

async function deleteUser(id) {
    if(!confirm("Are you sure you want to delete user " + id + "?")) return;
    const res = await fetch("/deleteUser", { method: "POST", body: id});
    showToast(await res.text(), "success");
    loadAllUsers();
}

function downloadReport() {
    showToast("Generating PDF Report...", "info");
    const element = byId('overview');
    const opt = {
        margin:       10,
        filename:     'EduTrack_Report.pdf',
        image:        { type: 'jpeg', quality: 0.98 },
        html2canvas:  { scale: 2 },
        jsPDF:        { unit: 'mm', format: 'a4', orientation: 'portrait' }
    };
    html2pdf().set(opt).from(element).save().then(()=>showToast("Downloaded!", "success"));
}

function logout() {
    localStorage.clear();
    window.location.href = "/";
}

function escapeHtml(text) {
    if(!text) return "";
    return text.toString()
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function getFacePageInputs() {
    return {
        studentId: byId("studentId") ? byId("studentId").value.trim() : "",
        subject: localStorage.getItem("subject") || ""
    };
}

let currentFaceDescriptor = null;
let faceModelsReady = false;
let storedFaceDescriptor = null;
let globalFaceMatcher = null;
let autoAttendanceActive = false;
let autoScanInterval = null;
let autoRecognitionRunning = false;
let markedStudents = new Set();

function descriptorToString(descriptor) {
    return descriptor.join(";");
}

function parseDescriptorString(text) {
    return text
        .split(";")
        .map(item => Number(item.trim()))
        .filter(item => !Number.isNaN(item));
}

function stopAutoScan() {
    if (autoScanInterval) {
        clearInterval(autoScanInterval);
        autoScanInterval = null;
    }
    autoAttendanceActive = false;
    autoRecognitionRunning = false;
}

function startAutoScan(video, subject) {
    stopAutoScan();
    autoAttendanceActive = true;
    autoScanInterval = setInterval(async () => {
        if (!autoAttendanceActive || autoRecognitionRunning) {
            return;
        }
        if (!video || video.paused || video.readyState < 2) {
            return;
        }
        await detectAndRecognizeFace(video, subject);
    }, 500);
}

async function detectAndRecognizeFace(video, subject) {
    autoRecognitionRunning = true;
    try {
        const options = new faceapi.TinyFaceDetectorOptions({ inputSize: 416, scoreThreshold: 0.3 });
        let detection = await faceapi
            .detectSingleFace(video, options)
            .withFaceLandmarks()
            .withFaceDescriptor();

        if (!detection) {
            detection = await faceapi
                .detectSingleFace(video, new faceapi.SsdMobilenetv1Options({ minConfidence: 0.3 }))
                .withFaceLandmarks()
                .withFaceDescriptor();
        }

        const canvas = byId('canvas');
        if (!canvas) return;
        const ctx = canvas.getContext('2d');
        const displaySize = { width: video.width, height: video.height };
        faceapi.matchDimensions(canvas, displaySize);

        if (!detection) {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            return;
        }

        const resizedDetection = faceapi.resizeResults(detection, displaySize);
        ctx.clearRect(0, 0, canvas.width, canvas.height);

        currentFaceDescriptor = detection.descriptor;

        if (globalFaceMatcher) {
            const bestMatch = globalFaceMatcher.findBestMatch(currentFaceDescriptor);
            
            const box = resizedDetection.detection.box;
            const drawBox = new faceapi.draw.DrawBox(box, { label: bestMatch.toString() });
            drawBox.draw(canvas);

            if (bestMatch.label !== 'unknown') {
                if (!markedStudents.has(bestMatch.label)) {
                    await autoSubmitAttendance(bestMatch.label, subject, bestMatch.distance);
                }
            }
        } else {
            faceapi.draw.drawDetections(canvas, resizedDetection);
        }

    } catch (error) {
        console.warn("Auto face recognition error:", error);
    } finally {
        autoRecognitionRunning = false;
    }
}

async function autoSubmitAttendance(studentId, subject, distance) {
    if (markedStudents.has(studentId)) {
        return;
    }

    markedStudents.add(studentId);
    const status = byId("status");
    const output = byId("faceOutput");
    status.innerText = `Face recognized for ${studentId}. Checking location...`;
    output.innerText = `Recognition distance: ${distance.toFixed(4)}\nFetching geolocation...`;

    navigator.geolocation.getCurrentPosition(async pos => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        try {
            const today = new Date().toISOString().slice(0, 10);
            const response = await fetch("/addAttendance", {
                method: "POST",
                body: studentId + "," + subject + "," + today + ",Present," + lat + "," + lng + "," + distance
            });
            const message = await response.text();
            if (!response.ok) {
                status.innerText = "Unable to save attendance automatically.";
                output.innerText = message;
                return;
            }

            status.innerText = `Attendance automatically marked for ${studentId}.`;
            output.innerText = message + "\nRecognition distance: " + distance.toFixed(4);
        } catch (error) {
            status.innerText = "Auto attendance failed.";
            output.innerText = error;
        }
    }, err => {
        status.innerText = "Blocked.";
        output.innerText = "Location required!";
    });
}

async function initFacePage() {
    const status = byId("status");
    const output = byId("faceOutput");
    const userId = localStorage.getItem("userId");
    const role = localStorage.getItem("userRole");

    if (!userId || role !== "teacher") {
        status.innerText = "Face attendance is available only for logged-in teachers.";
        output.innerText = "Please log in as a teacher to use face recognition attendance.";
        if(byId("startCameraBtn")) byId("startCameraBtn").disabled = true;
        if(byId("stopCameraBtn")) byId("stopCameraBtn").disabled = true;
        if(byId("captureFaceBtn")) byId("captureFaceBtn").disabled = true;
        if(byId("enrollFaceBtn")) byId("enrollFaceBtn").disabled = true;
        if(byId("markAttendanceBtn")) byId("markAttendanceBtn").disabled = true;
        return;
    }

    try {
        status.innerText = "Loading face recognition models...";
        const weightSources = [
            "/models",
            "https://cdn.jsdelivr.net/gh/justadudewhohacks/face-api.js@master/weights",
            "https://justadudewhohacks.github.io/face-api.js/models",
            "https://cdn.jsdelivr.net/npm/@vladmandic/face-api/model/"
        ];

        let loaded = false;
        let lastError = null;
        for (const weightsUrl of weightSources) {
            try {
                await faceapi.nets.tinyFaceDetector.loadFromUri(weightsUrl);
                await faceapi.nets.faceLandmark68Net.loadFromUri(weightsUrl);
                await faceapi.nets.faceRecognitionNet.loadFromUri(weightsUrl);
                await faceapi.nets.ssdMobilenetv1.loadFromUri(weightsUrl);
                loaded = true;
                status.innerText = "Face recognition models loaded. Enter student details and open the camera.";
                faceModelsReady = true;
                break;
            } catch (error) {
                lastError = error;
            }
        }

        if (!loaded) {
            throw lastError;
        }
    } catch (error) {
        status.innerText = "Unable to load face models from CDN. Face recognition will not work until models load.";
        output.innerText = error;
        faceModelsReady = false;
    } finally {
        if(byId("startCameraBtn")) byId("startCameraBtn").disabled = false;
        if(byId("stopCameraBtn")) byId("stopCameraBtn").disabled = true;
        if(byId("captureFaceBtn")) byId("captureFaceBtn").disabled = true;
        if(byId("enrollFaceBtn")) byId("enrollFaceBtn").disabled = true;
        if(byId("markAttendanceBtn")) byId("markAttendanceBtn").disabled = true;
    }
}

async function startVideo() {
    const role = localStorage.getItem("userRole");
    const output = byId("faceOutput");
    const status = byId("status");
    const video = byId("video");
    const { studentId, subject } = getFacePageInputs();

    if (role !== "teacher") {
        alert("Only teachers can open the camera for face attendance.");
        return;
    }

    if (!subject) {
        console.warn("No subject assigned to this teacher. Auto-attendance will fail to save.");
    }

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        status.innerText = "Camera access is not supported by this browser.";
        return;
    }

    try {
        let hasFaceData = false;
        const storedResponse = await fetch("/getAllFaces");
        const storedText = await storedResponse.text();

        if (!storedText || storedText.startsWith("No face data")) {
            status.innerText = "No enrolled face data found. Opening camera for enrollment.";
            output.innerText = "Please capture a face and enter a Student ID to enroll.";
        } else {
            const lines = storedText.trim().split("|");
            const labeledDescriptors = [];
            for (const line of lines) {
                if (!line) continue;
                const parts = line.split("=");
                if (parts.length === 2) {
                    const desc = parseDescriptorString(parts[1]);
                    if (desc.length > 0) {
                        labeledDescriptors.push(new faceapi.LabeledFaceDescriptors(parts[0], [new Float32Array(desc)]));
                    }
                }
            }
            if (labeledDescriptors.length > 0) {
                globalFaceMatcher = new faceapi.FaceMatcher(labeledDescriptors, 0.5);
                hasFaceData = true;
            }
        }

        const stream = await navigator.mediaDevices.getUserMedia({ video: true });
        video.srcObject = stream;
        video.setAttribute('playsinline', 'true');
        video.muted = true;
        video.addEventListener('loadedmetadata', () => {
            video.play().catch(() => {});
        });
        await video.play();
        
        if(byId("startCameraBtn")) byId("startCameraBtn").disabled = true;
        if(byId("stopCameraBtn")) byId("stopCameraBtn").disabled = false;
        if(byId("captureFaceBtn")) byId("captureFaceBtn").disabled = false;
        currentFaceDescriptor = null;
        markedStudents.clear();

        if (hasFaceData) {
            status.innerText = "Camera started. Auto face scan is now active. Any enrolled student entering the frame will be marked present.";
            output.innerText = "";
            startAutoScan(video, subject);
        }
    } catch (error) {
        status.innerText = "Unable to start camera. Please allow camera access or try another browser.";
        output.innerText = error;
    }
}

function stopVideo() {
    stopAutoScan();
    const video = byId("video");
    const canvas = byId("canvas");
    if(canvas) {
        canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
    }
    if (video && video.srcObject) {
        const tracks = video.srcObject.getTracks();
        tracks.forEach(track => track.stop());
        video.srcObject = null;
    }
    
    if(byId("startCameraBtn")) byId("startCameraBtn").disabled = false;
    if(byId("stopCameraBtn")) byId("stopCameraBtn").disabled = true;
    if(byId("captureFaceBtn")) byId("captureFaceBtn").disabled = true;
    if(byId("enrollFaceBtn")) byId("enrollFaceBtn").disabled = true;
    if(byId("markAttendanceBtn")) byId("markAttendanceBtn").disabled = true;
    
    byId("status").innerText = "Camera stopped.";
    byId("faceOutput").innerText = "";
}

async function captureFace() {
    const video = byId("video");
    const output = byId("faceOutput");
    const status = byId("status");

    if (!faceModelsReady) {
        status.innerText = "Face models are not ready yet.";
        output.innerText = "Please wait for model loading to complete or refresh the page.";
        return;
    }

    try {
        status.innerText = "Analyzing frame, please hold still...";
        
        if (video.readyState < 2) {
            await new Promise(resolve => {
                video.onloadeddata = () => resolve();
            });
        }

        const options = new faceapi.TinyFaceDetectorOptions({ inputSize: 320, scoreThreshold: 0.3 });
        let detection = await faceapi
            .detectSingleFace(video, options)
            .withFaceLandmarks()
            .withFaceDescriptor();

        if (!detection) {
            detection = await faceapi
                .detectSingleFace(video, new faceapi.SsdMobilenetv1Options({ minConfidence: 0.3 }))
                .withFaceLandmarks()
                .withFaceDescriptor();
        }

        if (!detection) {
            status.innerText = "No clear face detected. Try again.";
            output.innerText = "Make sure the student's face is visible in the webcam preview and well-lit.";
            return;
        }

        currentFaceDescriptor = detection.descriptor;
        status.innerText = "Student face captured.";
        output.innerText = "Face captured successfully. You can enroll or mark attendance now.";
        if(byId("enrollFaceBtn")) byId("enrollFaceBtn").disabled = false;
        if(byId("markAttendanceBtn")) byId("markAttendanceBtn").disabled = false;
    } catch (error) {
        status.innerText = "Face capture failed.";
        output.innerText = error;
    }
}

async function enrollFace() {
    const role = localStorage.getItem("userRole");
    const output = byId("faceOutput");
    const status = byId("status");
    const { studentId } = getFacePageInputs();

    if (role !== "admin") {
        alert("Only admins can enroll face data for students.");
        return;
    }

    if (!studentId) {
        status.innerText = "Enter the student ID before enrolling face data.";
        return;
    }

    if (!currentFaceDescriptor) {
        output.innerText = "Capture the student's face before enrolling.";
        return;
    }

    const descriptor = descriptorToString(currentFaceDescriptor);

    try {
        const response = await fetch("/saveFace", {
            method: "POST",
            body: studentId + "," + descriptor
        });

        const message = await response.text();
        status.innerText = "Face data saved for student " + studentId + ".";
        output.innerText = message;
        
        if (byId("resetFaceBtn")) byId("resetFaceBtn").disabled = false;
        if (byId("captureFaceBtn")) byId("captureFaceBtn").disabled = true;
        if (byId("enrollFaceBtn")) byId("enrollFaceBtn").disabled = true;
    } catch (error) {
        status.innerText = "Failed to save face data.";
        output.innerText = error;
    }
}

function resetFaceCapture() {
    if (byId("studentId")) byId("studentId").value = "";
    currentFaceDescriptor = null;
    
    if (byId("captureFaceBtn")) byId("captureFaceBtn").disabled = false;
    if (byId("enrollFaceBtn")) byId("enrollFaceBtn").disabled = true;
    if (byId("resetFaceBtn")) byId("resetFaceBtn").disabled = true;
    
    const canvas = byId('canvas');
    if (canvas) {
        canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
    }
    
    if (byId("status")) byId("status").innerText = "Ready to capture next face.";
    if (byId("faceOutput")) byId("faceOutput").innerText = "";
}

async function markAttendanceFace() {
    const role = localStorage.getItem("userRole");
    const output = byId("faceOutput");
    const status = byId("status");
    const { studentId, subject } = getFacePageInputs();

    if (role !== "teacher") {
        alert("Only teachers can mark attendance by face recognition.");
        return;
    }

    if (!studentId) {
        status.innerText = "Enter the student ID before marking attendance.";
        return;
    }
    
    if (!subject) {
        status.innerText = "Your account has no assigned subject. Cannot mark attendance.";
        return;
    }

    if (!currentFaceDescriptor) {
        output.innerText = "Capture the student's face before marking attendance.";
        return;
    }

    try {
        const response = await fetch("/getFace?id=" + encodeURIComponent(studentId));
        const stored = await response.text();

        if (!stored || stored.startsWith("No face data")) {
            output.innerText = "No enrolled face data found for student " + studentId + ". Please enroll their face data first.";
            return;
        }

        const storedDescriptor = parseDescriptorString(stored.trim());
        if (storedDescriptor.length !== currentFaceDescriptor.length) {
            output.innerText = "Saved face descriptor has an unexpected length. Re-enroll the student face.";
            status.innerText = "Recognition failed.";
            return;
        }

        const distance = faceapi.euclideanDistance(currentFaceDescriptor, storedDescriptor);
        const threshold = 0.5;

        if (distance > threshold) {
            output.innerText = "Face not recognized. Distance " + distance.toFixed(4) + ". Try again or use manual attendance.";
            status.innerText = "Recognition failed.";
            return;
        }

        status.innerText = "Face recognized. Locating...";
        navigator.geolocation.getCurrentPosition(async pos => {
            const lat = pos.coords.latitude;
            const lng = pos.coords.longitude;
            try {
                const today = new Date().toISOString().slice(0, 10);
                const attendanceResponse = await fetch("/addAttendance", {
                    method: "POST",
                    body: studentId + "," + subject + "," + today + ",Present," + lat + "," + lng + "," + distance
                });

                const attendanceMessage = await attendanceResponse.text();
                if (!attendanceResponse.ok) {
                    status.innerText = "Attendance service returned an error.";
                    output.innerText = attendanceMessage;
                    return;
                }

                status.innerText = "Attendance marked successfully for " + studentId + ".";
                output.innerText = attendanceMessage + "\nRecognition distance: " + distance.toFixed(4);
            } catch (error) {
                status.innerText = "Failed to mark attendance.";
                output.innerText = error;
            }
        }, err => {
            status.innerText = "Attendance Blocked.";
            output.innerText = "Error: Campus Geolocation required!";
        });
    } catch (error) {
        status.innerText = "Failed to process attendance.";
        output.innerText = error;
    }
}

function manualAttendance() {
    const role = localStorage.getItem("userRole");
    const status = byId("status");
    const output = byId("faceOutput");
    const { studentId, subject } = getFacePageInputs();

    if (role !== "teacher") {
        alert("Only teachers can manually add attendance.");
        return;
    }

    if (!studentId) {
        status.innerText = "Enter the student ID before manual attendance.";
        return;
    }
    
    if (!subject) {
        status.innerText = "Your account has no assigned subject. Cannot mark manual attendance.";
        return;
    }

    const attendanceStatus = prompt("Attendance status for " + studentId + " (Present or Absent)", "Present");
    if (!attendanceStatus) {
        return;
    }

    const today = new Date().toISOString().slice(0, 10);

    fetch("/addAttendance", {
        method: "POST",
        body: studentId + "," + subject + "," + today + "," + attendanceStatus
    })
    .then(r => r.text())
    .then(msg => {
        status.innerText = "Manual attendance saved.";
        output.innerText = msg;
    })
    .catch(error => {
        status.innerText = "Failed to save manual attendance.";
        output.innerText = error;
    });
}

// ADMIN PORTAL LOGIC
async function initFacePageAdmin() {
    const status = byId("status");
    const output = byId("faceOutput");

    try {
        status.innerText = "Loading AI vision models for Enrollment...";
        const weightSources = [
            "/models",
            "https://cdn.jsdelivr.net/gh/justadudewhohacks/face-api.js@master/weights",
            "https://justadudewhohacks.github.io/face-api.js/models",
            "https://cdn.jsdelivr.net/npm/@vladmandic/face-api/model/"
        ];

        let loaded = false;
        for (const weightsUrl of weightSources) {
            try {
                await faceapi.nets.tinyFaceDetector.loadFromUri(weightsUrl);
                await faceapi.nets.faceLandmark68Net.loadFromUri(weightsUrl);
                await faceapi.nets.faceRecognitionNet.loadFromUri(weightsUrl);
                await faceapi.nets.ssdMobilenetv1.loadFromUri(weightsUrl);
                loaded = true;
                status.innerText = "Face models initialized. Ready to enroll.";
                faceModelsReady = true;
                break;
            } catch (error) { }
        }

        if (!loaded) throw new Error("Could not load models");
    } catch (error) {
        status.innerText = "Unable to load face models.";
        faceModelsReady = false;
    } finally {
        if(byId("startCameraBtn")) byId("startCameraBtn").disabled = false;
    }
}

async function startVideoAdmin() {
    const status = byId("status");
    const video = byId("video");

    try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: true });
        video.srcObject = stream;
        video.setAttribute('playsinline', 'true');
        video.muted = true;
        video.addEventListener('loadedmetadata', () => { video.play().catch(() => {}); });
        await video.play();
        
        if(byId("startCameraBtn")) byId("startCameraBtn").disabled = true;
        if(byId("stopCameraBtn")) byId("stopCameraBtn").disabled = false;
        if(byId("captureFaceBtn")) byId("captureFaceBtn").disabled = false;
        currentFaceDescriptor = null;

        status.innerText = "Camera started for enrollment.";
    } catch (error) {
        status.innerText = "Unable to start camera.";
    }
}
