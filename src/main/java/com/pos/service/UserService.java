package com.pos.service;

import com.pos.model.Role;
import com.pos.model.User;

public final class UserService {
    public static final String USERNAME = "username";
    public static final String FULL_NAME = "full_name";
    public static final String ROLE = "role";
    public static final String PIN = "pin";
    public static final String PIN_CONFIRM = "pin_confirm";
    public static final String DUPLICATE_USERNAME_MESSAGE = "This username is already taken";
    public static final int MIN_PIN_LENGTH = 4;
    public static final int MAX_PIN_LENGTH = 6;

    private UserService() {
    }

    public static User validateNewUser(String username, String fullName, Role role, String pin, String pinConfirm) throws ValidationException {
        String cleanUsername = username == null ? "" : username.trim();
        if (!cleanUsername.matches("[A-Za-z0-9._-]{3,50}")) {
            throw new ValidationException("username", "Username must be 3 to 50 letters, digits, dots, dashes or underscores");
        } else {
            String cleanName = fullName == null ? "" : fullName.trim();
            if (cleanName.isEmpty()) {
                throw new ValidationException("full_name", "Full name is required");
            } else if (cleanName.length() > 100) {
                throw new ValidationException("full_name", "Full name can be at most 100 characters");
            } else if (role == null) {
                throw new ValidationException("role", "Choose a role");
            } else if (pin == null || !pin.matches("\\d{4,6}")) {
                throw new ValidationException("pin", "PIN must be 4 to 6 digits");
            } else if (!pin.equals(pinConfirm)) {
                throw new ValidationException("pin_confirm", "The two PINs do not match");
            } else {
                return new User(0, cleanUsername, cleanName, role, true);
            }
        }
    }
}
