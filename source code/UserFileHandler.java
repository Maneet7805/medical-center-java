package assignment;

import java.io.*;
import java.util.*;

public class UserFileHandler {

    // ---------------- READ ----------------
    // Reads all users of a given role from the corresponding file.
    // Each line is split by "|" and returned as a list of String arrays.
    public static List<String[]> readUsersFromFile(String role) {
        List<String[]> users = new ArrayList<>();
        String fileName = role + "s.txt";

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                users.add(line.split("\\|"));
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + fileName);
        }

        return users;
    }

    // ---------------- WRITE ----------------
    // Writes a list of users (String arrays) back to the file for the given role.
    // Each user array is joined with "|" as the delimiter.
    public static void writeUsersToFile(String role, List<String[]> users) {
        String fileName = role + "s.txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (String[] user : users) {
                writer.write(String.join("|", user));
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing file: " + fileName);
        }
    }

    // ---------------- DELETE ----------------
    // Deletes a user with the specified ID from the role's file.
    // Reads all users, removes the matching ID, then writes back the updated list.
    public static void deleteUserById(String role, String id) {
        List<String[]> users = readUsersFromFile(role);
        users.removeIf(user -> user[0].equals(id));
        writeUsersToFile(role, users);
    }

    // ---------------- UPDATE ----------------
    // Updates the user with the specified ID using the provided newData array.
    // Reads all users, replaces the matching entry, then writes back the updated list.
    public static void updateUserById(String role, String id, String[] newData) {
        List<String[]> users = readUsersFromFile(role);
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i)[0].equals(id)) {
                users.set(i, newData);
                break;
            }
        }
        writeUsersToFile(role, users);
    }
}
