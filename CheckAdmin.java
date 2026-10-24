import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckAdmin {
    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:c:/Users/gupta/OneDrive/Documents/EdutrackWeb/edutrack.db")) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, role, pass FROM Users WHERE role = 'admin' OR id LIKE 'ADM%'");
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("User: " + rs.getString("id") + " Role: " + rs.getString("role") + " Pass: " + rs.getString("pass"));
            }
            if (!found) System.out.println("No admin found!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
