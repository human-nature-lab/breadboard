package models;

import com.fasterxml.jackson.databind.JsonNode;
import play.mvc.WebSocket;

import java.util.Timer;
import java.util.concurrent.LinkedBlockingQueue;

public class ThrottledWebSocketOut {
  private WebSocket.Out<JsonNode> out;
  private LinkedBlockingQueue<JsonNode> queue = new LinkedBlockingQueue<JsonNode>();
  private long lastSent = Long.MAX_VALUE;
  private long wait;
  private Timer timer = new Timer();
  private boolean scheduled = false;

  public ThrottledWebSocketOut(WebSocket.Out<JsonNode> out, long wait) {
    this.out = out;
    this.wait = wait;
  }

  public synchronized void write(JsonNode message) {
    /*
    if(System.currentTimeMillis() - lastSent < wait || scheduled) {
      queue.offer(message);
      if (! scheduled) {
        timer.schedule(new TimerTask() {
          @Override
          public void run() {
            //System.out.println("TimerTask.run()");
            ObjectNode outNode = Json.newObject();

            ArrayList<JsonNode> jsonNodes = new ArrayList<JsonNode>();
            while (! queue.isEmpty()) {
              jsonNodes.add(queue.poll());
            }
            outNode.put("queuedMessages", Json.toJson(jsonNodes));

            out.write(outNode);

            lastSent = System.currentTimeMillis();
            scheduled = false;
          }
        }, wait);
        scheduled = true;
      }
    } else {
      out.write(message);
      //System.out.println("queue.size() = " + queue.size());

      lastSent = System.currentTimeMillis();
    }
    */
    out.write(message);
  }

  public void close () {
    this.out.close();
  }
}
