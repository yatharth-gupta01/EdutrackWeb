import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class InsertAdmin {
    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:c:/Users/gupta/OneDrive/Documents/EdutrackWeb/edutrack.db")) {
            PreparedStatement pstmt = conn.prepareStatement("INSERT INTO Users (role, id, name, pass, subject) VALUES (?, ?, ?, ?, ?)");
            pstmt.setString(1, "admin");
            pstmt.setString(2, "ADM001");
            pstmt.setString(3, "Super Admin");
            pstmt.setString(4, "adminpass");
            pstmt.setString(5, null);
            pstmt.executeUpdate();
            System.out.println("Admin inserted successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
