import com.avaje.ebean.Ebean;
import models.Experiment;
import models.User;
import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.db.DB;
import play.libs.Yaml;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Global extends GlobalSettings {
  public void onStart(Application app) {
    //InitialData.insert(app);
  }

  static class InitialData {
    public static void insert(Application app) {
      Logger.info("Importing initial data:");

      @SuppressWarnings("unchecked")
      Map<String, List<Object>> all = (Map<String, List<Object>>) Yaml.load("initial-data.yml");

      if (Ebean.find(Experiment.class).findRowCount() == 0) {
        Logger.info("\t importing Experiment");
        Collection allExperiments = all.get("experiments");
        Ebean.save(allExperiments);

        resetSeq("experiments", "experiments_seq");
      }

      if (Ebean.find(User.class).findRowCount() == 0) {
        Logger.info("\t importing User");
        Ebean.save(all.get("users"));

        for (Object user : all.get("users")) {
          Ebean.saveManyToManyAssociations(user, "ownedExperiments");
        }
      }

    }

    /**
     * This is used to reset the auto generated sequence for the h2 database.
     * This is useful because after the initial-data is loaded, the tables' auto generated values are not setting
     * to the right value which is causing #11 in the issue tracker.
     *
     * @param tableName
     * @param seqName
     */
    private static void resetSeq(String tableName, String seqName) {
      Connection connection = DB.getConnection();
      Statement selectStatement = null;

      Long maxId = null;
      try {
        selectStatement = connection.createStatement();
        ResultSet rs = selectStatement.executeQuery("select max(id) from " + tableName + ";");
        while (rs.next()) {
          maxId = rs.getLong(1);
        }

      } catch (SQLException e) {
        Logger.error("Unable to get the current max id.", e);
      } finally {
        if (selectStatement != null) {
          try {
            selectStatement.close();
          } catch (SQLException e) {
            Logger.error("Unable to close the statement.", e);
          }
        }
      }

      if (maxId != null) {
        Statement statement = null;
        try {
          statement = connection.createStatement();
          statement.execute("ALTER SEQUENCE " + seqName + " RESTART WITH " + (maxId + 1) + ";");
        } catch (SQLException e) {
          Logger.error("Unable to alter the sequence.", e);
        } finally {
          if (statement != null) {
            try {
              statement.close();
            } catch (SQLException e) {
              Logger.error("Unable to close the statement.", e);
            }
          }
          try {
            connection.close();
          } catch (SQLException e) {
            Logger.error("Unable to close the connection.", e);
          }
        }
      }
    }
  }
}
