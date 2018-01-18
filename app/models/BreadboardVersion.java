package models;

import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.List;

@Entity
@Table(name = "breadboard_version")
public class BreadboardVersion extends Model {
  public String version;

  public static Finder<Long, BreadboardVersion> find = new Finder(Long.class, BreadboardVersion.class);

  public static List<BreadboardVersion> findAll() {
    return find.all();
  }
}
