package models;

import com.fasterxml.jackson.databind.JsonNode;
import play.mvc.WebSocket;

public class ThrottledWebSocketOut {
  private WebSocket.Out<JsonNode> out;

  public ThrottledWebSocketOut(WebSocket.Out<JsonNode> out, long wait) {
    this.out = out;
  }

  public void write(JsonNode message) {
    out.write(message);
  }

  public void close () {
    this.out.close();
  }
}
