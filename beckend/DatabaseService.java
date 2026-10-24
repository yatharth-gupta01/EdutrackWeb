package beckend;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

public class DatabaseService {

    private static final String URL = "jdbc:sqlite:edutrack.db";

    public static void initDb() {
        try (Connection conn = DriverManager.getConnection(URL)) {
            // Read and execute schema
            Path schemaPath = Paths.get("database_schema.sql");
            if (Files.exists(schemaPath)) {
                String schema = Files.readString(schemaPath);
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(schema);
                }
            }
            migrateOldData(conn);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void migrateOldData(Connection conn) {
        try {
            // Check if Users table is empty before migrating
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Users")) {
                if (rs.getInt(1) > 0) return; // Already migrated
            }

            Path dataDir = Paths.get("data");
            if (!Files.exists(dataDir)) return;

            // Migrate Users
            Path usersFile = dataDir.resolve("users.txt");
            if (Files.exists(usersFile)) {
                try (BufferedReader br = new BufferedReader(new FileReader(usersFile.toFile()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] d = line.split(",");
                        try (PreparedStatement pstmt = conn.prepareStatement(
                                "INSERT INTO Users (role, id, name, pass, subject) VALUES (?, ?, ?, ?, ?)")) {
                            pstmt.setString(1, d[0]);
                            pstmt.setString(2, d[1]);
                            pstmt.setString(3, d[2]);
                            pstmt.setString(4, d[3]);
                            pstmt.setString(5, d.length > 4 ? d[4] : null);
                            pstmt.executeUpdate();
                        }
                    }
                }
            }

            // Migrate Faces
            Path facesFile = dataDir.resolve("faces.txt");
            if (Files.exists(facesFile)) {
                try (BufferedReader br = new BufferedReader(new FileReader(facesFile.toFile()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] d = line.split(",", 2);
                        if (d.length >= 2) {
                            try (PreparedStatement pstmt = conn.prepareStatement(
                                    "INSERT INTO FaceData (user_id, descriptor) VALUES (?, ?)")) {
                                pstmt.setString(1, d[0]);
                                pstmt.setString(2, d[1]);
                                pstmt.executeUpdate();
                            }
                        }
                    }
                }
            }

            // Migrate Attendance
            Path attFile = dataDir.resolve("attendance.txt");
            if (Files.exists(attFile)) {
                try (BufferedReader br = new BufferedReader(new FileReader(attFile.toFile()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] d = line.split(",");
                        if (d.length >= 4) {
                            try (PreparedStatement pstmt = conn.prepareStatement(
                                    "INSERT INTO Attendance (user_id, subject, date, status) VALUES (?, ?, ?, ?)")) {
                                pstmt.setString(1, d[0]);
                                pstmt.setString(2, d[1]);
                                pstmt.setString(3, d[2]);
                                pstmt.setString(4, d[3]);
                                pstmt.executeUpdate();
                            }
                        }
                    }
                }
            }

            // Migrate Marks
            Path marksFile = dataDir.resolve("marks.txt");
            if (Files.exists(marksFile)) {
                try (BufferedReader br = new BufferedReader(new FileReader(marksFile.toFile()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] d = line.split(",");
                        if (d.length >= 4) {
                            try (PreparedStatement pstmt = conn.prepareStatement(
                                    "INSERT INTO Marks (user_id, date, assignment, marks) VALUES (?, ?, ?, ?)")) {
                                pstmt.setString(1, d[0]);
                                pstmt.setString(2, d[1]);
                                pstmt.setString(3, d.length == 4 ? d[2] : d[3]);
                                pstmt.setString(4, d.length == 4 ? d[3] : d[4]);
                                pstmt.executeUpdate();
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Database migration error: " + e.getMessage());
        }
    }

    public static void logAudit(String userId, String action, String description) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO AuditLogs (user_id, action, description) VALUES (?, ?, ?)")) {
            pstmt.setString(1, userId);
            pstmt.setString(2, action);
            pstmt.setString(3, description);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // LOGIN returns a Session Token alongside role info
    public static String authenticateAndCreateSession(String id, String pass) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement("SELECT role, subject FROM Users WHERE id = ? AND pass = ?")) {
            pstmt.setString(1, id);
            pstmt.setString(2, pass);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String role = rs.getString("role");
                    String subject = rs.getString("subject");
                    String token = UUID.randomUUID().toString();
                    
                    try (PreparedStatement tStmt = conn.prepareStatement("INSERT INTO Sessions (token, user_id) VALUES (?, ?)")) {
                        tStmt.setString(1, token);
                        tStmt.setString(2, id);
                        tStmt.executeUpdate();
                    }
                    logAudit(id, "LOGIN", "User logged in");
                    
                    String resp = role;
                    if (subject != null && !subject.isEmpty()) {
                        resp += "," + subject;
                    }
                    return token + ":" + resp;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Invalid";
    }

    public static String getSessionUserId(String token) {
        if (token == null || token.isBlank()) return null;
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement("SELECT user_id FROM Sessions WHERE token = ?")) {
            pstmt.setString(1, token);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("user_id");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public static void logoutSession(String token) {
        if (token == null) return;
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM Sessions WHERE token = ?")) {
            pstmt.setString(1, token);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveAttendance(String id, String sub, String date, String status, double lat, double lng) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO Attendance (user_id, subject, date, status, location_lat, location_lng) VALUES (?, ?, ?, ?, ?, ?)")) {
            pstmt.setString(1, id);
            pstmt.setString(2, sub);
            pstmt.setString(3, date);
            pstmt.setString(4, status);
            pstmt.setDouble(5, lat);
            pstmt.setDouble(6, lng);
            pstmt.executeUpdate();
            logAudit(id, "ATTENDANCE", "Marked " + status + " for " + sub);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getAttendance(String id) {
        StringBuilder res = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement("SELECT date, subject, status FROM Attendance WHERE user_id = ? ORDER BY id ASC")) {
            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    res.append(rs.getString("date")).append(",")
                       .append(rs.getString("subject")).append(",")
                       .append(rs.getString("status")).append("\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res.toString().trim();
    }

    public static void saveFaceDescriptor(String id, String descriptor) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO FaceData (user_id, descriptor) VALUES (?, ?) ON CONFLICT(user_id) DO UPDATE SET descriptor = ?")) {
            pstmt.setString(1, id);
            pstmt.setString(2, descriptor);
            pstmt.setString(3, descriptor);
            pstmt.executeUpdate();
            logAudit(id, "FACE_DATA", "Updated facial descriptor");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getFaceDescriptor(String id) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement("SELECT descriptor FROM FaceData WHERE user_id = ?")) {
            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("descriptor");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String getAllFaceDescriptors() {
        StringBuilder res = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT user_id, descriptor FROM FaceData")) {
            while (rs.next()) {
                res.append(rs.getString("user_id")).append("=").append(rs.getString("descriptor")).append("|");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        String result = res.toString();
        if (result.endsWith("|")) result = result.substring(0, result.length() - 1);
        return result;
    }

    public static void saveMarks(String teacherId, String studentId, String date, String ass, String marks) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO Marks (user_id, date, assignment, marks) VALUES (?, ?, ?, ?)")) {
            pstmt.setString(1, studentId);
            pstmt.setString(2, date);
            pstmt.setString(3, ass);
            pstmt.setString(4, marks);
            pstmt.executeUpdate();
            logAudit(teacherId, "MARKS_UPDATE", "Updated marks for " + studentId + " - " + ass);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getMarks(String id) {
        StringBuilder res = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement("SELECT date, assignment, marks FROM Marks WHERE user_id = ? ORDER BY id ASC")) {
            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    res.append(rs.getString("date")).append(",")
                       .append(rs.getString("assignment")).append(",")
                       .append(rs.getString("marks")).append("\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res.toString().trim();
    }

    public static String getAllUsers() {
        StringBuilder res = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT role, id, name, pass, subject FROM Users")) {
            while (rs.next()) {
                res.append(rs.getString("role")).append(",")
                   .append(rs.getString("id")).append(",")
                   .append(rs.getString("name")).append(",")
                   .append(rs.getString("pass"));
                String subject = rs.getString("subject");
                if (subject != null && !subject.isEmpty()) {
                    res.append(",").append(subject);
                }
                res.append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res.toString().trim();
    }

    public static void addUser(String adminId, String role, String id, String name, String pass, String subject) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO Users (role, id, name, pass, subject) VALUES (?, ?, ?, ?, ?)")) {
            pstmt.setString(1, role);
            pstmt.setString(2, id);
            pstmt.setString(3, name);
            pstmt.setString(4, pass);
            pstmt.setString(5, subject);
            pstmt.executeUpdate();
            logAudit(adminId, "USER_ADD", "Added user " + id + " (" + role + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteUser(String adminId, String id) {
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM Users WHERE id = ?")) {
            pstmt.setString(1, id);
            pstmt.executeUpdate();
            logAudit(adminId, "USER_DELETE", "Deleted user " + id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Advanced Analytics
    public static String getSubjectAttendanceStats(String id) {
        StringBuilder res = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT subject, " +
                     "SUM(CASE WHEN status='Present' THEN 1 ELSE 0 END) as present, " +
                     "COUNT(*) as total " +
                     "FROM Attendance WHERE user_id = ? GROUP BY subject")) {
            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    res.append(rs.getString("subject")).append(":")
                       .append(rs.getInt("present")).append("/")
                       .append(rs.getInt("total")).append("|");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res.toString().trim();
    }
}
