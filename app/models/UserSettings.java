package models;


import org.apache.commons.lang3.StringUtils;
import org.mindrot.jbcrypt.BCrypt;

public class UserSettings {

  public User user;
  public String email;
  public String currentPassword;
  public String newPassword;
  public String confirmPassword;

  public String validate() {
    if (StringUtils.isEmpty(email)) {
      return "Email can't be empty";
    }

    user = User.findByEmail(email);
    if (user == null) {
      return "User is not found for the email:" + email;
    }

    //TODO: better validation like regex?
    if (StringUtils.isEmpty(currentPassword)) {
      return "Current password is missing";
    }
    if (!BCrypt.checkpw(currentPassword, user.password)) {
      return "Current password doesn't match user's password";
    }
    if (StringUtils.isEmpty(newPassword)) {
      return "New password is missing";
    }
    if (!newPassword.equals(confirmPassword)) {
      return "Confirm Password doesn't match the new password";
    }
    return null;
  }


}
