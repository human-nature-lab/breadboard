package controllers;

import play.libs.WS;
import play.mvc.Result;

import java.io.File;

import static play.libs.F.*;
import static play.libs.F.Promise;
import static play.mvc.Controller.response;
import static play.mvc.Results.ok;

public class BundlesController {

    public static Promise<Result> asset(String filePath){
        String mode = play.Play.application().configuration().getString("application.mode");
        if(mode.toUpperCase().equals("DEV")) {
            return WS.url("http://localhost:8765/bundles/" + filePath).get().map(new Function<WS.Response, Result>() {
                public Result apply(WS.Response res) {
                    String contentType = res.getHeader("Content-Type") == null ? res.getHeader("Content-Type") : "application/octet-stream";
                    response().setContentType(contentType);
                    return ok(res.getBodyAsStream());
                }
            });
        } else {
            // Seems dumb that we need two promises here to pass a value to Promise<Result> -> https://www.playframework.com/documentation/2.2.x/JavaAsync#How-to-create-a-Promise<Result>
            // Would likely be better with lambda functions in 1.8
            Promise<String> sp = Promise.pure(filePath);
            return sp.map(new Function<String, Result>() {
                public Result apply(String filePath) {
                    File f = play.Play.application().getFile("/public/bundles/" + filePath);
                    return ok(f);
                }
            });
        }

    }

}
