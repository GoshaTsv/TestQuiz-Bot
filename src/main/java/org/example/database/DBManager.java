package org.example.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.example.classes.StudentClass;
import org.example.classes.User;
import org.example.classes.appLinking.Test;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;

public class DBManager {
    private static final String USER = System.getenv("DB_USER");
    private static final String PASSWORD = System.getenv("DB_PASS");
    private static final String URL = System.getenv("DB_URL");

    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PASSWORD);

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(600000);
        config.setLeakDetectionThreshold(60000);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtSqlLimit", "2048");
        config.addDataSourceProperty("userServerPrepStmts", "true");

        try {
            dataSource = new HikariDataSource(config);
            System.out.println("Connection pull initialized");
        } catch (Exception e) {
            System.out.println("Failed to initialize connection pull: " + e.getMessage());
            throw new RuntimeException("Connection pull wasn't initialized");
        }
    }

    public static Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    public static int doesClassExit(long teacherId, String name) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while checking the existence of the class");
                return 2;
            }

            try (PreparedStatement st = connection.prepareStatement("SELECT * FROM public.classes WHERE name = ? AND teacher_id = ?")) {
                st.setString(1, name.toLowerCase());
                st.setLong(2, teacherId);

                try (ResultSet res = st.executeQuery()) {
                    return res.next() ? 1 : 0;
                }
            }
        } catch (SQLException e) {
            System.out.println("An exception while checking the existence of the class");
            return 2;
        }
    }

    public static boolean registerAccount(String username, long chatId) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while registering account");
                return false;
            }

            try (PreparedStatement st = connection.prepareStatement("INSERT INTO public.users (chat_id, username) VALUES (?, ?)")) {
                st.setLong(1, chatId);
                st.setString(2, username);
                st.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.out.println("An exception while registering account: " + e.getMessage());
            return false;
        }
    }

    public static boolean createClass(String name, long teacherId, ArrayList<Long> students, long totalAnswers, long correctAnswers, long experience) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while creating new class");
                return false;
            }

            try (PreparedStatement st = connection.prepareStatement("INSERT INTO public.classes (name, teacher_id, students, totalanswerscount, correctanswerscount, experience) VALUES (?, ?, ?, ?, ?, ?)")) {
                st.setString(1, name.toLowerCase());
                st.setLong(2, teacherId);
                st.setArray(3, connection.createArrayOf("BIGINT", students.toArray()));
                st.setLong(4, totalAnswers);
                st.setLong(5, correctAnswers);
                st.setLong(6, experience);

                st.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.out.println("An exception while creating creating new class: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteClass(long teacherId, String name) { // new method delete class
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while deleting " + teacherId + "'s class: " + name);
                return false;
            }

            try (PreparedStatement st = connection.prepareStatement("DELETE FROM public.classes WHERE teacher_id = ? AND name = ?")) {
                st.setLong(1, teacherId);
                st.setString(2, name);
                st.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.out.println("An exception while deleting " + teacherId + "'s class (" + name + "): " + e.getMessage());
            return false;
        }
    }

    public static int doesUserExist(String username) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while searching account");
                return 2;
            }

            try (PreparedStatement st = connection.prepareStatement("SELECT * FROM public.users WHERE username = ?")) {
                st.setString(1, username);
                try (ResultSet res = st.executeQuery()) {
                    return res.next() ? 1 : 0;
                }
            }
        } catch (SQLException e) {
            System.out.println("An exception while searching an account: " + e.getMessage());
            return 2;
        }
    }

    public static boolean createTest(String content, long userId) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while creating new test");
                return false;
            }

            try (PreparedStatement st = connection.prepareStatement("INSERT INTO public.tests (user_id, content) VALUES (?, ?::jsonb)")) {
                st.setLong(1, userId);
                st.setString(2, content);

                st.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.out.println("An exception while creating new test: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteTest(long userId, String content) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while deleting " + userId + "'s test: " + content);
                return false;
            }

            try (PreparedStatement st = connection.prepareStatement("DELETE FROM public.tests WHERE user_id = ? AND content = ?::jsonb")) {
                st.setLong(1, userId);
                st.setString(2, content);
                st.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.out.println("An exception while deleting " + userId + "'s test (" + content + "): " + e.getMessage());
            return false;
        }
    }

    public static StudentClass getClass(String name, long teacherId) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while getting " + teacherId + "'s class: " + name);
                return null;
            }

            try (PreparedStatement st = connection.prepareStatement("SELECT * FROM public.classes WHERE teacher_id = ? and name = ?")) {
                st.setLong(1, teacherId);
                st.setString(2, name.toLowerCase());

                try (ResultSet res = st.executeQuery()) {
                    if (!res.next())
                        return null;
                    long experience = res.getLong("experience");
                    long totalanswers = res.getLong("totalanswerscount");
                    long correctanswers = res.getLong("correctanswerscount");

                    Long[] javaStudentsArray = (Long[]) res.getArray("students").getArray();

                    return new StudentClass(teacherId, name, new ArrayList<>(Arrays.asList(javaStudentsArray)), totalanswers, correctanswers, experience);
                }
            }
        } catch (SQLException e) {
            System.out.println("An exception while getting " + teacherId + "'s class: " + name);
            return null;
        }
    }

    public static ArrayList<StudentClass> getClasses(long chatId) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while getting " + chatId + "'s classes");
                return null;
            }

            try (PreparedStatement st = connection.prepareStatement("SELECT * FROM public.classes WHERE teacher_id = ?")) {
                st.setLong(1, chatId);
                try (ResultSet res = st.executeQuery()) {
                    ArrayList<StudentClass> classes = new ArrayList<>();
                    while (res.next()) {
                        Array studentsArray = res.getArray("students");
                        Long[] javaStudentArray = (Long[]) studentsArray.getArray(); // long -> Long
                        ArrayList<Long> students = new ArrayList<>(Arrays.asList(javaStudentArray));
                        long experience = res.getLong("experience");
                        long totalanswers = res.getLong("totalanswerscount");
                        long correctanswers = res.getLong("correctanswerscount");
                        StudentClass studentClass = new StudentClass(chatId, res.getString("name"), students, totalanswers, correctanswers, experience);

                        classes.add(studentClass);
                    }
                    return classes;
                }
            }
        } catch (SQLException e) {
            System.out.println("An exception while getting " + chatId + "'s classes");
            return null;
        }
    }

    public static ArrayList<Long> getIdsByUsernames(ArrayList<String> usernames) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while getting " + usernames + " ids");
                return null;
            }

            ArrayList<Long> ids = new ArrayList<>();
            for (String username : usernames) {
                try (PreparedStatement st = connection.prepareStatement("SELECT chat_id FROM public.users WHERE username = ?")) {
                    st.setString(1, username);
                    try (ResultSet res = st.executeQuery()) {
                        if (!res.next()) {
                            System.out.println("User with username = " + username + " doesn't exit in the database");
                            return null;
                        }
                        ids.add(res.getLong("chat_id"));
                    }
                } catch (SQLException e) {
                    System.out.println("An exception while getting " + username + "'s chat id: " + e.getMessage());
                    return null;
                }
            }
            return ids;
        } catch (SQLException e) {
            System.out.println("An exception while getting ids: " + e.getMessage());
            return null;
        }
    }

    public static ArrayList<String> getUsernamesByIds(ArrayList<Long> ids) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while getting " + ids + "'s usernames");
                return null;
            }

            ArrayList<String> usernames = new ArrayList<>();
            for (Long id : ids) {
                try (PreparedStatement st = connection.prepareStatement("SELECT username FROM public.users WHERE chat_id = ?")) {
                    st.setLong(1, id);
                    try (ResultSet res = st.executeQuery()) {
                        if (!res.next()) {
                            System.out.println("User with id = " + id + " doesn't exit in the database");
                            return null;
                        }
                        usernames.add(res.getString("username"));
                    }
                } catch (SQLException e) {
                    System.out.println("An exception while getting " + id + "'s username: " + e.getMessage());
                    return null;
                }
            }
            return usernames;
        } catch (SQLException e) {
            System.out.println("An exception while getting usernames: " + e.getMessage());
            return null;
        }
    }

    //added a new method getTests(), which, well, gets tests and returns them as an arraylist of Tests
    public static ArrayList<Test> getTests(long chatId) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while getting tests");
                return null;
            }

            try (PreparedStatement st = connection.prepareStatement("SELECT content FROM public.tests WHERE user_id = ?")) {
                st.setLong(1, chatId);
                try (ResultSet res = st.executeQuery()) {
                    ArrayList<Test> tests = new ArrayList<>();
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    while (res.next()) {
                        Test test = gson.fromJson(res.getString("content"), Test.class);
                        tests.add(test);
                    }
                    return tests;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error when getting tests: " + e.getMessage());
            return null;
        }
    }

    public static Test getTest(long chatId, String name) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while getting test");
                return null;
            }

            try (PreparedStatement st = connection.prepareStatement("SELECT content FROM public.tests WHERE user_id = ?")) {
                st.setLong(1, chatId);
                try (ResultSet res = st.executeQuery()) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    while (res.next()) {
                        Test test = gson.fromJson(res.getString("content"), Test.class);
                        if (test.getTestName().equalsIgnoreCase(name)) {
                            return test;
                        }
                    }
                    System.out.println("Returning null");
                    return new Test(null, null);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error when getting test: " + e.getMessage());
            return null;
        }
    }

    public static String getTestContent(long chatId, String name) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while getting tests");
                return null;
            }

            try (PreparedStatement st = connection.prepareStatement("SELECT content FROM public.tests WHERE user_id = ?")) {
                st.setLong(1, chatId);
                try (ResultSet res = st.executeQuery()) {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    while (res.next()) {
                        Test test = gson.fromJson(res.getString("content"), Test.class);
                        if (test.getTestName().equalsIgnoreCase(name))
                            return res.getString("content");
                    }
                    return null;
                }
            }
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

    public static boolean updateUserLang(long chatId, String lang) {
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while updating user lang");
                return false;
            }

            try (PreparedStatement st = connection.prepareStatement("UPDATE public.users SET lang = ? WHERE chat_id = ?")) {
                st.setString(1, lang);
                st.setLong(2, chatId);

                st.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            System.out.println("An exception while updating user's lang: " + e.getMessage());
            return false;
        }
    }

    public static ArrayList<User> getUsers() { // new method get users
        try (Connection connection = getConnection()) {
            if (connection == null) {
                System.out.println("Connection became null while getting users");
                return null;
            }

            try (PreparedStatement st = connection.prepareStatement("SELECT * FROM public.users");
                 ResultSet res = st.executeQuery()) {

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

                    users.add(new User(res.getLong("chat_id"), "default", res.getString("lang"), res.getInt("permission_level"), classes.size(), tests.size(), -1, null, res.getInt("autodelete")));
                }
                return users;
            }
        } catch (SQLException e) {
            System.out.println("An exception while getting users: " + e.getMessage());
            return null;
        }
    }
}