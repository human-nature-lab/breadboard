package controllers;

import play.*;
import play.mvc.*;
import play.mvc.Http.*;
import org.apache.commons.lang3.ArrayUtils;

import models.*;

public class SecuredClient extends Security.Authenticator 
{
    @Override
    public String getUsername(Context ctx) 
    {
        return ctx.session().get("id");
    }
    
    @Override
    public Result onUnauthorized(Context ctx) 
    {
    	String[] pathComponents = ctx.request().path().split("/");
    	String experimentId = pathComponents[ArrayUtils.indexOf(pathComponents, "game") + 1];
    	String experimentInstanceId = pathComponents[ArrayUtils.indexOf(pathComponents, "game") + 2];
        return redirect(routes.ClientLogin.login(experimentId, experimentInstanceId));
    }
}
