package beckend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Server {

    private static final Path FRONTEND_DIR = Paths.get("frontend");

    // Campus Coordinates (for mock test)
    private static final double CAMPUS_LAT = 28.7041; 
    private static final double CAMPUS_LNG = 77.1025;
    private static final double MAX_DISTANCE_METERS = 9999999.0; // Infinite for testing 

    public static void main(String[] args) throws Exception {

        DatabaseService.initDb();
        System.out.println("Database Initialized!");

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // LOGIN
        server.createContext("/login", exchange -> {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "Method Not Allowed");
                return;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            String body = br.readLine();
            if (body == null || body.isBlank()) {
                send(exchange, 400, "Invalid");
                return;
            }
            String[] data = body.split(",");
            if (data.length < 2) {
                send(exchange, 400, "Invalid");
                return;
            }

            String authResponse = DatabaseService.authenticateAndCreateSession(data[0], data[1]);
            if ("Invalid".equals(authResponse)) {
                send(exchange, 401, "Invalid");
                return;
            }

            // authResponse format: token:role,subject
            String[] parts = authResponse.split(":");
            String token = parts[0];
            String payload = parts[1];

            exchange.getResponseHeaders().add("Set-Cookie", "session_token=" + token + "; HttpOnly; Path=/");
            send(exchange, 200, payload);
        });

        // LOGOUT
        server.createContext("/logout", exchange -> {
            String token = getSessionToken(exchange);
            if (token != null) {
                DatabaseService.logoutSession(token);
            }
            exchange.getResponseHeaders().add("Set-Cookie", "session_token=; HttpOnly; Path=/; Max-Age=0");
            send(exchange, 200, "Logged out");
        });

        // =================PROTECTED ENDPOINTS=================
        
        // ADD ATTENDANCE
        server.createContext("/addAttendance", exchange -> {
            String userId = authMiddleware(exchange);
            if (userId == null) return;

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "Method Not Allowed");
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            String body = br.readLine();
            if (body == null || body.isBlank()) {
                send(exchange, 400, "Invalid attendance data");
                return;
            }

            // Expected format: studentId, subject, date, status, lat, lng, confidence
            String[] d = body.split(",");
            if (d.length < 4) {
                send(exchange, 400, "Invalid attendance data: needs more parsing");
                return;
            }

            double lat = 0.0, lng = 0.0;
            if (d.length >= 6) {
                try {
                    lat = Double.parseDouble(d[4]);
                    lng = Double.parseDouble(d[5]);
                } catch (Exception ignored) {}
            }

            // Distance Validation
            if (lat != 0.0 && lng != 0.0) {
                double distance = calculateDistance(lat, lng, CAMPUS_LAT, CAMPUS_LNG);
                if (distance > MAX_DISTANCE_METERS) {
                    send(exchange, 403, "Out of Campus bounds!");
                    return;
                }
            }

            // Student marking own attendance needs identity match or Teacher marking it
            DatabaseService.saveAttendance(d[0], d[1], d[2], d[3], lat, lng);
            send(exchange, 200, "Attendance Saved");
        });

        // GET ATTENDANCE
        server.createContext("/getAttendance", exchange -> {
            if (authMiddleware(exchange) == null) return;
            
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.contains("=")) {
                send(exchange, 400, "Missing student id");
                return;
            }
            String id = query.split("=", 2)[1];
            send(exchange, 200, DatabaseService.getAttendance(id));
        });

        // ANALYTICS GET SUBJECT ATTENDANCE
        server.createContext("/api/analytics/subjectStats", exchange -> {
            if (authMiddleware(exchange) == null) return;
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("=")) {
                String id = query.split("=", 2)[1];
                send(exchange, 200, DatabaseService.getSubjectAttendanceStats(id));
            } else {
                send(exchange, 400, "Missing ID");
            }
        });

        // SAVE/GET FACES
        server.createContext("/saveFace", exchange -> {
            if (authMiddleware(exchange) == null) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            String body = br.readLine();
            String[] d = body.split(",", 2);
            DatabaseService.saveFaceDescriptor(d[0], d[1]);
            send(exchange, 200, "Face data saved");
        });

        server.createContext("/getFace", exchange -> {
            if (authMiddleware(exchange) == null) return;
            String query = exchange.getRequestURI().getQuery();
            String id = query.split("=", 2)[1];
            String faceData = DatabaseService.getFaceDescriptor(id);
            send(exchange, 200, faceData);
        });

        server.createContext("/getAllFaces", exchange -> {
            if (authMiddleware(exchange) == null) return;
            send(exchange, 200, DatabaseService.getAllFaceDescriptors());
        });

        // SAVE MARKS
        server.createContext("/addMarks", exchange -> {
            String teacherId = authMiddleware(exchange);
            if (teacherId == null) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            String body = br.readLine();
            String[] d = body.split(",");
            DatabaseService.saveMarks(teacherId, d[0], d[1], d[2], d[3]);
            send(exchange, 200, "Marks Saved");
        });

        server.createContext("/getMarks", exchange -> {
            if (authMiddleware(exchange) == null) return;
            String query = exchange.getRequestURI().getQuery();
            String id = query.split("=", 2)[1];
            send(exchange, 200, DatabaseService.getMarks(id));
        });

        // ADMIN ROUTES
        server.createContext("/getAllUsers", exchange -> {
            if (authMiddleware(exchange) == null) return;
            send(exchange, 200, DatabaseService.getAllUsers());
        });

        server.createContext("/addUser", exchange -> {
            String adminId = authMiddleware(exchange);
            if (adminId == null) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            String body = br.readLine();
            String[] d = body.split(",");
            String subject = d.length > 4 ? d[4] : "";
            DatabaseService.addUser(adminId, d[0], d[1], d[2], d[3], subject);
            send(exchange, 200, "User Added");
        });

        server.createContext("/deleteUser", exchange -> {
            String adminId = authMiddleware(exchange);
            if (adminId == null) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            String id = br.readLine();
            DatabaseService.deleteUser(adminId, id.trim());
            send(exchange, 200, "User Deleted");
        });

        // STATIC FRONTEND FILES - Generic handler for all frontend assets
        server.createContext("/", e -> {
            String path = e.getRequestURI().getPath();
            // Route known SPA routes to their HTML files
            if (path.equals("/") || path.equals("/login") || path.equals("/login.html")) {
                serve(e, "login.html");
            } else if (path.equals("/dashboard") || path.equals("/dashboard.html")) {
                serve(e, "dashboard.html");
            } else if (path.equals("/admin") || path.equals("/admin.html")) {
                serve(e, "admin.html");
            } else if (path.equals("/face") || path.equals("/face.html")) {
                serve(e, "face.html");
            } else {
                // Generic static file serving - strip leading slash and resolve in frontend dir
                String relativePath = path.startsWith("/") ? path.substring(1) : path;
                Path frontendAbsolute = FRONTEND_DIR.toAbsolutePath().normalize();
                Path filePath = frontendAbsolute.resolve(relativePath).normalize();
                // Security: ensure the resolved path is still inside frontend dir
                if (!filePath.startsWith(frontendAbsolute)) {
                    send(e, 403, "Forbidden");
                    return;
                }
                if (!Files.exists(filePath)) {
                    send(e, 404, "File not found: " + relativePath);
                    return;
                }
                byte[] data = Files.readAllBytes(filePath);
                e.getResponseHeaders().set("Content-Type", contentType(relativePath));
                e.sendResponseHeaders(200, data.length);
                try (java.io.OutputStream os = e.getResponseBody()) { os.write(data); }
                e.close();
            }
        });

        server.start();
        System.out.println("Server running at http://localhost:8080");
    }

    // ================= MIDDLEWARE ================= //

    static String getSessionToken(HttpExchange exchange) {
        String cookies = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookies != null) {
            for (String cookie : cookies.split(";")) {
                if (cookie.trim().startsWith("session_token=")) {
                    return cookie.trim().substring("session_token=".length());
                }
            }
        }
        return null;
    }

    static String authMiddleware(HttpExchange exchange) throws IOException {
        String token = getSessionToken(exchange);
        if (token == null) {
            send(exchange, 401, "Unauthorized: No Session Cookie");
            return null;
        }
        String userId = DatabaseService.getSessionUserId(token);
        if (userId == null) {
            send(exchange, 401, "Unauthorized: Invalid or Expired Session");
            return null;
        }
        return userId;
    }

    // ================= HELPERS ================= //

    static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        if ((lat1 == lat2) && (lon1 == lon2)) return 0;
        double theta = lon1 - lon2;
        double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) + 
                      Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
        dist = Math.acos(dist);
        dist = Math.toDegrees(dist);
        dist = dist * 60 * 1.1515 * 1.609344 * 1000; // to meters
        return Math.abs(dist);
    }

    static void send(HttpExchange e, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        e.sendResponseHeaders(statusCode, bytes.length);
        try (java.io.OutputStream os = e.getResponseBody()) {
            os.write(bytes);
        }
        e.close();
    }

    static void serve(HttpExchange e, String path) throws IOException {
        Path filePath = FRONTEND_DIR.resolve(path).normalize();
        if (!Files.exists(filePath)) {
            // Also redirect specific missing routes to dashboard or login
            // for simplicity just returning 404
            send(e, 404, "File not found");
            return;
        }

        byte[] data = Files.readAllBytes(filePath);
        e.getResponseHeaders().set("Content-Type", contentType(path));
        e.sendResponseHeaders(200, data.length);
        try (java.io.OutputStream os = e.getResponseBody()) {
            os.write(data);
        }
        e.close();
    }

    static String contentType(String path) {
        if (path.endsWith(".css")) return "text/css; charset=UTF-8";
        if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (path.endsWith(".json")) return "application/json; charset=UTF-8";
        if (path.endsWith(".bin")) return "application/octet-stream";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".ico")) return "image/x-icon";
        // Model weight shards (no extension)
        if (path.contains("-shard") || path.contains("model-shard")) return "application/octet-stream";
        return "text/html; charset=UTF-8";
    }
}
