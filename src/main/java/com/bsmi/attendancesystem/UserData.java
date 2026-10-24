package com.bsmi.attendancesystem;

public class UserData {
    private final Integer userId;
    private final String userName;
    private final String userRole;
    public UserData(Integer userId, String userName, String userRole) {
        this.userId = userId;
        this.userName = userName;
        this.userRole = userRole;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserRole() {
        return userRole;
    }
}
