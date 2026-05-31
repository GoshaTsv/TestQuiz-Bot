package org.example.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.example.classes.StudentClass;
import org.example.classes.User;
import org.example.classes.appLinking.Test;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class DBManager {
    private static final String USER = System.getenv("DB_USER");
    private static final String PASSWORD = System.getenv("DB_PASS");
    private static final String URL = System.getenv("DB_URL"); // changed the dbs name to match the one hosted locally

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static int doesClassExit(long teacherId, String name) {
        Connection connection = getConnection();
        if (connection == null) {
            System.out.println("Connection became null while checking the existence of the class");
            return 2;
        }

        try {
            PreparedStatement st = connection.prepareStatement("SELECT * FROM public.classes WHERE name = ? AND teacher_id = ?");

            st.setString(1, name);
            st.setLong(2, teacherId);

            ResultSet res = st.executeQuery();
            return res.next() ? 1 : 0;
        } catch (SQLException e) {
            System.out.println("An exception while checking the existence of the class");
            return 2;
        }
    }

    public static boolean registerAccount(String username, long chatId) {
        Connection connection = getConnection();
        if (connection == null) {
            System.out.println("Connection became null while registering account");
            return false;
        }
        try {
            PreparedStatement st = connection.prepareStatement("INSERT INTO public.users (chat_id, username) VALUES (?, ?)");
            st.setLong(1, chatId);
            st.setString(2, username);
            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("An exception while registering account: " + e.getMessage());
            return false;
        }
    }

    public static boolean createClass(String name, long teacherId, ArrayList<Long> students) {
        Connection connection = getConnection();
        if (connection == null) {
            System.out.println("Connection became null while creating new class");
            return false;
        }

        try {
            PreparedStatement st = connection.prepareStatement("INSERT INTO public.classes (name, teacher_id, students) VALUES (?, ?, ?)");

            st.setString(1, name);
            st.setLong(2, teacherId);
            st.setArray(3, connection.createArrayOf("BIGINT", students.toArray()));

            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("An exception while creating creating new class: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteClass(long teacherId, String name) { // new method delete class
        Connection connection = getConnection();
        if (connection == null) {
            System.out.println("Connection became null while deleting " + teacherId + "'s class: " + name);
            return false;
        }

        try {
            PreparedStatement st = connection.prepareStatement("DELETE FROM public.classes WHERE teacher_id = ? AND name = ?");
            st.setLong(1, teacherId);
            st.setString(2, name);
            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("An exception while deleting " + teacherId + "'s class (" + name + "): " + e.getMessage());
            return false;
        }
    }

    public static int doesUserExist(String username) {
        Connection connection = getConnection();
        if (connection == null) {
            System.out.println("Connection became null while searching account");
            return 2;
        }
        try {
            PreparedStatement st = connection.prepareStatement("SELECT * FROM public.users WHERE username = ?");
            st.setString(1, username);
            ResultSet res = st.executeQuery();
            return res.next() ? 1 : 0;
        } catch (SQLException e) {
            System.out.println("An exception while searching an account: " + e.getMessage());
            return 2;
        }
    }

    public static boolean createTest(String content, long userId) {
        Connection connection = getConnection();
        if (connection == null) {
            System.out.println("Connection became null while creating new test");
            return false;
        }

        try {
            PreparedStatement st = connection.prepareStatement("INSERT INTO public.tests (user_id, content) VALUES (?, ?::jsonb)");
            st.setLong(1, userId);
            st.setString(2, content);

            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("An exception while creating new test: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteTest(long userId, String content) {
        Connection connection = getConnection();
        if (connection == null) {
            System.out.println("Connection became null while deleting " + userId + "'s test: " + content);
            return false;
        }

        try {
            PreparedStatement st = connection.prepareStatement("DELETE FROM public.tests WHERE user_id = ? AND content = ?::jsonb");
            st.setLong(1, userId);
            st.setString(2, content);
            st.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("An exception while deleting " + userId + "'s test (" + content + "): " + e.getMessage());
            return false;
        }
    }

    public static StudentClass getClass(String name, long teacherId) {
        Connection connection = getConnection();
        if (connection == null) {
            System.out.println("Connection became null while getting " + teacherId + "'s class: " + name);
            return null;
        }

        try {
            PreparedStatement st = connection.prepareStatement("SELECT * FROM public.classes WHERE teacher_id = ? and name = ?");
            st.setLong(1, teacherId);
            st.setString(2, name);

            ResultSet res = st.executeQuery();
            if (!res.next())
                return null;

            Long[] javaStudentsArray = (Long[]) res.getArray("students").getArray(); //changed primitive type to object

            return new StudentClass(teacherId, name, new ArrayList<>(Arrays.asList(javaStudentsArray)));
        } catch (SQLException e) {
            System.out.println("An exception while getting " + teacherId + "'s class: " + name);
            return null;
        }
    }

    public static ArrayList<StudentClass> getClasses(long chatId) {
        Connection connection = getConnection();
        if (connection == null) {
            System.out.println("Connection became null while getting " + chatId + "'s classes");
            return null;
        }

        try {
            PreparedStatement st = connection.prepareStatement("SELECT * FROM public.classes WHERE teacher_id = ?");
            st.setLong(1, chatId);
            ResultSet res = st.executeQuery();

            ArrayList<StudentClass> classes = new ArrayList<>();
            System.out.println("Starting getting " + chatId + "'s classes");
            while (res.next()) {
                Array studentsArray = res.getArray("students");
                Long[] javaStudentArray = (Long[]) studentsArray.getArray(); // long -> Long
                ArrayList<Long> students = new ArrayList<>(Arrays.asList(javaStudentArray));

                StudentClass studentClass = new StudentClass(chatId, res.getString("name"), students);

                classes.add(studentClass);
            }
            return classes;
        } catch (SQLException e) {
            System.out.println("An exception while getting " + chatId + "'s classes");
            return null;
        }
    }

    public static ArrayList<Long> getIdsByUsernames(ArrayList<String> usernames) {
        Connection connection = getConnection();
        if (connection == null) {
            System.out.println("Connection became null while getting " + usernames + " ids");
            return null;
        }

        ArrayList<Long> ids = new ArrayList<>();
        for (String username : usernames) {
            try {
                PreparedStatement st = connection.prepareStatement("SELECT chat_id FROM public.users WHERE username = ?");
                st.setString(1, username);
                ResultSet res = st.executeQuery();
                if (!res.next()) {
                    System.out.println("User with username = " + username + " doesn't exit is database");
                    return null;
                }
                ids.add(res.getLong("chat_id"));
            } catch (SQLException e) {
                System.out.println("An exception while getting " + username + "'s chat id: " + e.getMessage());
                return null;
            }
        }
        return ids;
    }
    //added a new method getTests(), which, well, gets tests and returns them as an arraylist of Tests
    public static ArrayList<Test> getTests(long chatId){
        Connection connection = getConnection();
        if (connection == null){
            System.out.println("Connection became null while getting tests");
            return null;
        }
        try {
            PreparedStatement st = connection.prepareStatement("SELECT content FROM public.tests WHERE user_id = ?");
            st.setLong(1, chatId);
            ResultSet res = st.executeQuery();

            ArrayList<Test> tests = new ArrayList<>();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            while (res.next()){
                Test test = gson.fromJson(res.getString("content"), Test.class);
                tests.add(test);
            }
            return tests;
        } catch (SQLException e) {
            System.out.println("Error when getting tests: " + e.getMessage());
            return null;
        }
    }

    public static Test getTest(long chatId, String name){
        Connection connection = getConnection();
        if (connection == null){
            System.out.println("Connection became null while getting test");
            return null;
        }
        try {
            PreparedStatement st = connection.prepareStatement("SELECT content FROM public.tests WHERE user_id = ?");
            st.setLong(1, chatId);
            System.out.println("Getting result set");
            ResultSet res = st.executeQuery();

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            while (res.next()){
                Test test = gson.fromJson(res.getString("content"), Test.class);
                System.out.println("Got new test: " + test);
                if (test.getTestName().equalsIgnoreCase(name)) {
                    System.out.println("Test found: " + test);
                    return test;
                }
                System.out.println("Getting next");
            }
            System.out.println("Returning null");
            return new Test(null, null);
        } catch (SQLException e) {
            System.out.println("Error when getting test: " + e.getMessage());
            return null;
        }
    }

    public static String getTestContent(long chatId, String name){
        Connection connection = getConnection();
        if (connection == null){
            System.out.println("Connection became null while getting tests");
            return null;
        }
        try {
            PreparedStatement st = connection.prepareStatement("SELECT content FROM public.tests WHERE user_id = ?");
            st.setLong(1, chatId);
            ResultSet res = st.executeQuery();

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            while (res.next()){
                Test test = gson.fromJson(res.getString("content"), Test.class);
                if (test.getTestName().equalsIgnoreCase(name))
                    return res.getString("content");
            }
            return null;
        } catch (SQLException e) {
            System.out.println("Error when getting test: " + e.getMessage());
            return null;
        }
    }

    public static int doesTestExit(long chatId, String name) {
        Test test = getTest(chatId, name.toLowerCase());
        if (test == null)
            return 2;
        if (test.getTestName() == null)
            return 0;
        return 1;
    }

    public static ArrayList<User> getUsers() { // new method get users
        Connection connection = getConnection();
        if (connection == null) {
            System.out.println("Connection became null while getting users");
            return null;
        }

        try {
            PreparedStatement st = connection.prepareStatement("SELECT * FROM public.users");
            ResultSet res = st.executeQuery();

            ArrayList<User> users = new ArrayList<>();
            while (res.next()) {
                ArrayList<StudentClass> classes = getClasses(res.getLong("chat_id"));

                if (classes == null) {
                    System.out.println("Classes became null while getting users");
                    return null;
                }

                ArrayList<Test> tests = getTests(res.getLong("chat_id"));

                if (tests == null) {
                    System.out.println("Tests became null while getting users");
                    return null;
                }

                System.out.println(res.getLong("chat_id") + "'s tests: " + tests);
                System.out.println(res.getLong("chat_id") + "'s classes: " + classes);

                users.add(new User(res.getLong("chat_id"), "default", classes.size(), tests.size(), -1, null));
            }
            return users;
        } catch (SQLException e) {
            System.out.println("An exception while getting users: " + e.getMessage());
            return null;
        }
    }
}
