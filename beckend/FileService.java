package beckend;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileService {

    private static final Path DATA_DIR = Paths.get("data");
    private static final Path USERS_FILE = DATA_DIR.resolve("users.txt");
    private static final Path ATTENDANCE_FILE = DATA_DIR.resolve("attendance.txt");
    private static final Path MARKS_FILE = DATA_DIR.resolve("marks.txt");
    private static final Path FACES_FILE = DATA_DIR.resolve("faces.txt");

    // LOGIN
    public static String authenticate(String id, String pass) {

        try (BufferedReader br = new BufferedReader(
                new FileReader(USERS_FILE.toFile()))) {

            String line;

            while ((line = br.readLine()) != null) {

                String[] d = line.split(",");

                if (d[1].equals(id) && d[3].equals(pass)) {
                    if (d.length > 4) {
                        return d[0] + "," + d[4];
                    }
                    return d[0];
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Invalid";
    }

    // SAVE ATTENDANCE
    public static void saveAttendance(String id, String sub, String date, String status) {

        try {
            Files.createDirectories(DATA_DIR);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        try (FileWriter fw = new FileWriter(ATTENDANCE_FILE.toFile(), true)) {

            fw.write(id + "," + sub + "," + date + "," + status + "\n");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // GET ATTENDANCE
    public static String getAttendance(String id) {
        StringBuilder res = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(ATTENDANCE_FILE.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] d = line.split(",");
                if (d[0].equals(id)) {
                    // return raw: date,subject,status
                    res.append(d[2]).append(",").append(d[1]).append(",").append(d[3]).append("\n");
                }
            }
        } catch (FileNotFoundException e) {
            return "";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res.toString().trim();
    }

    // SAVE FACE DESCRIPTOR
    public static void saveFaceDescriptor(String id, String descriptor) {

        try {
            Files.createDirectories(DATA_DIR);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        StringBuilder content = new StringBuilder();
        boolean updated = false;

        if (Files.exists(FACES_FILE)) {
            try (BufferedReader br = new BufferedReader(new FileReader(FACES_FILE.toFile()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] d = line.split(",", 2);
                    if (d.length < 2) {
                        continue;
                    }
                    if (d[0].equals(id)) {
                        content.append(id).append(",").append(descriptor).append("\n");
                        updated = true;
                    } else {
                        content.append(line).append("\n");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!updated) {
            content.append(id).append(",").append(descriptor).append("\n");
        }

        try (FileWriter fw = new FileWriter(FACES_FILE.toFile())) {
            fw.write(content.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getFaceDescriptor(String id) {
        if (!Files.exists(FACES_FILE)) {
            return "";
        }

        try (BufferedReader br = new BufferedReader(new FileReader(FACES_FILE.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] d = line.split(",", 2);
                if (d.length < 2) {
                    continue;
                }
                if (d[0].equals(id)) {
                    return d[1];
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public static String getAllFaceDescriptors() {
        if (!Files.exists(FACES_FILE)) {
            return "";
        }

        StringBuilder res = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(FACES_FILE.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] d = line.split(",", 2);
                if (d.length < 2) {
                    continue;
                }
                res.append(d[0]).append("=").append(d[1]).append("|");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String result = res.toString();
        if (result.endsWith("|")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    // SAVE MARKS
    public static void saveMarks(String id, String date, String ass, String marks) {

        try {
            Files.createDirectories(DATA_DIR);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        try (FileWriter fw = new FileWriter(MARKS_FILE.toFile(), true)) {

            fw.write(id + "," + date + "," + ass + "," + marks + "\n");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // GET MARKS
    public static String getMarks(String id) {
        StringBuilder res = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(MARKS_FILE.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] d = line.split(",");
                if (!d[0].equals(id)) continue;
                String date = "";
                String assignment = "";
                String marks = "";
                if (d.length == 4) {
                    date = d[1]; assignment = d[2]; marks = d[3];
                } else if (d.length >= 5) {
                    date = d[1]; assignment = d[3]; marks = d[4];
                }
                res.append(date).append(",").append(assignment).append(",").append(marks).append("\n");
            }
        } catch (FileNotFoundException e) {
            return "";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res.toString().trim();
    }

    // GET ALL USERS
    public static String getAllUsers() {
        StringBuilder res = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(USERS_FILE.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                res.append(line).append("\n");
            }
        } catch (FileNotFoundException e) {
            return "";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res.toString().trim();
    }

    // ADD USER
    public static void addUser(String role, String id, String name, String pass, String subject) {
        try (FileWriter fw = new FileWriter(USERS_FILE.toFile(), true)) {
            fw.write(role + "," + id + "," + name + "," + pass + (subject.isEmpty() ? "" : "," + subject) + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // DELETE USER
    public static void deleteUser(String id) {
        StringBuilder res = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(USERS_FILE.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] d = line.split(",");
                if (!d[1].equals(id)) {
                    res.append(line).append("\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        try (FileWriter fw = new FileWriter(USERS_FILE.toFile(), false)) {
            fw.write(res.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
