package models;

import org.apache.commons.lang3.StringUtils;

public class CreateFirstUser {

    public String email;
    public String newPassword;
    public String confirmPassword;

    public String validate() {
        if (StringUtils.isEmpty(email)) {
            return "Email can't be empty";
        }

        if (email.equals("test@test.com")) {
            return "Email can't be test@test.com";
        }

        //TODO: better validation like regex?
        if (StringUtils.isEmpty(newPassword)) {
            return "New password is missing";
        }

        if (!newPassword.equals(confirmPassword)) {
            return "Confirm Password doesn't match the new password";
        }
        return null;
    }
}
