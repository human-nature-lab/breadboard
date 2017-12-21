package controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.User;
import play.mvc.Http.Context;
import play.mvc.Result;
import play.mvc.Security;
import play.libs.Json;

public class Secured extends Security.Authenticator {
  @Override
  public String getUsername(Context ctx) {
    String uid = ctx.session().get("uid");
    User user = User.findByUID(uid);
    if(user != null) {
      return User.findByUID(uid).email;
    } else {
      return null;
    }
//    return ctx.session().get("email");
  }

  @Override
  public Result onUnauthorized(Context ctx) {
    ObjectNode result = Json.newObject();
    if (User.findRowCount() == 0) {
      result.put("status", "create-first-user");
    } else {
      result.put("status", "unauthorized");
    }
    result.put("message", "please login");
    return unauthorized(result);
//    return redirect(routes.Application.login());
  }
}
