import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class WipeDb {
    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:c:/Users/gupta/OneDrive/Documents/EdutrackWeb/edutrack.db")) {
            Statement stmt = conn.createStatement();
            int faces = stmt.executeUpdate("DELETE FROM FaceData");
            int attendance = stmt.executeUpdate("DELETE FROM Attendance");
            System.out.println("Deleted " + faces + " face signatures and " + attendance + " attendance records.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
