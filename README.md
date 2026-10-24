# EduTrack Pro - Smart AI Attendance & Academic Management System

EduTrack Pro is a full-stack, AI-powered academic management and attendance tracking platform. It leverages in-browser Deep Learning (via `face-api.js`), GPS Geofencing, and Role-Based Access Control (Admin, Teacher, Student) to eliminate proxy attendance and streamline academic workflows.

---

## Key Features

- **Biometric AI Face Recognition**: Client-side facial landmark detection and 128-dimensional embedding vector matching in real time using TensorFlow.js & `face-api.js`.
- **GPS Geofencing**: Validates geographic coordinates to verify the student's physical presence on campus using spherical trigonometry.
- **Role-Based Access Control (RBAC)**:
  - **Admin**: User directory management, system configuration, biometric face enrollment.
  - **Teacher**: AI camera auto-scan attendance, manual attendance override, assignment and marks management.
  - **Student**: Real-time attendance percentage, marks breakdown, interactive charts, and report generation.
- **Interactive Visualizations**: Dynamic charts powered by Chart.js (Attendance breakdown pie charts and marks trend bar graphs).
- **Security & Audit Trail**: Cookie-based session tokens with `HttpOnly` flags and full audit logging of administrative and academic events.

---

## Tech Stack

- **Backend**: Java (`com.sun.net.httpserver.HttpServer`, standard JDK)
- **Database**: SQLite via JDBC (`sqlite-jdbc`)
- **Frontend**: HTML5, Vanilla CSS3 (Custom design system with Dark/Light mode), JavaScript (ES6+)
- **Machine Learning**: `face-api.js` (SSD MobileNet V1, TinyFaceDetector, FaceLandmark68Net, FaceRecognitionNet)
- **Data Visualization**: Chart.js

---

## Project Structure

```
EdutrackWeb/
│
├── beckend/
│   ├── Server.java          # Core HTTP server, routing, cookies & geofencing validation
│   ├── DatabaseService.java # SQLite JDBC operations, CRUD, session management & audit logs
│   └── FileService.java     # Legacy flat-file service & migration helper
│
├── frontend/
│   ├── login.html           # Authentication portal
│   ├── dashboard.html       # Student & Teacher portal (SPA with Chart.js)
│   ├── admin.html           # Admin portal (User management & Face Enrollment)
│   ├── face.html            # Teacher attendance scanner with AI auto-detection
│   ├── script.js            # Frontend logic (face-api pipeline, geolocation, API calls)
│   ├── style.css            # Responsive UI design system (glassmorphism, theme toggle)
│   └── models/              # Pretrained neural network weights for face-api.js
│
├── database_schema.sql      # Schema definitions for SQLite tables
├── edutrack.db              # SQLite database
├── lib/
│   └── sqlite-jdbc.jar      # SQLite JDBC driver
│
└── CheckAdmin.java, InsertAdmin.java, WipeDb.java, DumpDb.java # Database management utilities
```

---

## Getting Started

### Prerequisites

- Java Development Kit (JDK 11 or higher)
- A modern web browser with webcam access (Chrome, Edge, Firefox)

### Running the Application

1. **Compile Backend**:
   ```bash
   javac -cp "lib/sqlite-jdbc.jar;." beckend/*.java
   ```

2. **Start the Server**:
   ```bash
   java -cp "lib/sqlite-jdbc.jar;." beckend.Server
   ```

3. **Access the Web Portal**:
   Open your browser and navigate to:
   ```
   http://localhost:8080/
   ```

### Default Credentials

| Role | User ID | Password | Access |
| :--- | :--- | :--- | :--- |
| **Admin** | `ADM001` | `adminpass` | `/admin` |
| **Teacher** | `TCH001` | `teachpass` | `/dashboard` & `/face` |
| **Student** | `STU001` | `stupass` | `/dashboard` |

*(To seed the default admin account on a fresh database, run: `java -cp "lib/sqlite-jdbc.jar;." InsertAdmin`)*

---

## License

This project is licensed under the MIT License.
