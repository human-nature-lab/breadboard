import com.avaje.ebean.Ebean;
import com.avaje.ebean.SqlRow;
import controllers.LanguageController;
import exceptions.BreadboardException;
import models.*;
import org.apache.commons.io.FileUtils;
import play.Play;
import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.db.DB;
import play.libs.F;
import play.libs.Yaml;
import play.mvc.Action;
import play.mvc.Http;

import java.io.*;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Global extends GlobalSettings {

  static Process process = null;

  @Override
  public void onStart(Application app) {

    String sql = "select count(*) as table_count from information_schema.tables where table_name = 'breadboard_version';";
    SqlRow versionTableCount = Ebean.createSqlQuery(sql).findUnique();
    String count = versionTableCount.getString("table_count");

    if (count.equals("0")) {

      // Create the breadboard_version table
      sql = "create table breadboard_version ( version varchar(255) ); ";
      Ebean.createSqlUpdate(sql).execute();
      // Update the version
      sql = "insert into breadboard_version values ('v2.3.0'); ";
      Ebean.createSqlUpdate(sql).execute();

      // Create the languages table
      sql = "create table if not exists languages ( id bigint not null, code varchar(8), name varchar(255), " +
          "constraint pk_language primary key (id) ); ";
      Ebean.createSqlUpdate(sql).execute();

      // Create the experiment_languages table
      sql = "create table if not exists experiments_languages " +
          "( experiments_id bigint not null, languages_id bigint not null, " +
          "foreign key (experiments_id) references experiments(id), " +
          "foreign key (languages_id) references languages(id) );";
      Ebean.createSqlUpdate(sql).execute();

      // Create the translations table
      sql = "create table if not exists translations ( id bigint not null, html text, content_id bigint not null, languages_id bigint not null," +
          "foreign key (content_id) references content(id)," +
          "foreign key (languages_id) references languages(id)," +
          "constraint pk_translations primary key (id) ); ";
      Ebean.createSqlUpdate(sql).execute();

      // Add the auto increment sequences
      sql = "create sequence if not exists languages_seq; ";
      Ebean.createSqlUpdate(sql).execute();
      sql = "create sequence if not exists experiments_languages_seq; ";
      Ebean.createSqlUpdate(sql).execute();
      sql = "create sequence if not exists translations_seq; ";
      Ebean.createSqlUpdate(sql).execute();

      // Add the uid column to experiments
      sql = "alter table experiments add column if not exists uid varchar(255); ";
      Ebean.createSqlUpdate(sql).execute();

      // Add default language column to users
      sql = "alter table users add column if not exists default_language_id bigint; ";
      Ebean.createSqlUpdate(sql).execute();

      sql = "alter table users add constraint fk_default_language_languages " +
          "foreign key (default_language_id) references languages (id) " +
          "on delete restrict on update restrict; ";
      Ebean.createSqlUpdate(sql).execute();

      sql = "create index if not exists ix_users_default_language on users (default_language_id); ";
      Ebean.createSqlUpdate(sql).execute();

      // Changes to support new AMT dialog
      sql = "alter table amt_assignments add column if not exists bonus_amount varchar(255); ";
      Ebean.createSqlUpdate(sql).execute();

      sql = "alter table amt_hits alter column experiment_instance_id set null; ";
      Ebean.createSqlUpdate(sql).execute();

      // Additional schema changes for older versions of breadboard pre v2.1
      sql = "alter table experiments add column if not exists client_graph text; ";
      Ebean.createSqlUpdate(sql).execute();
      sql = "alter table experiments add column if not exists client_html text; ";
      Ebean.createSqlUpdate(sql).execute();

      // Messages table
      sql = "create table messages (\n" +
          "  id                        bigint not null,\n" +
          "  message_uid               varchar(36),\n" +
          "  message_title             varchar(255),\n" +
          "  message_html              text,\n" +
          "  priority                  tinyint,\n" +
          "  auto_open                 bit,\n" +
          "  created_at                timestamp,\n" +
          "  dismissed_at              timestamp\n" +
          ");\n";
      Ebean.createSqlUpdate(sql).execute();

      // Migrate data
      LanguageController.seedLanguages();

      Language english = Language.findByIso3("eng");
      if (english == null) {
        Logger.debug("Unable to find english in languages.");
        english = Language.findAll().get(0);
      }

      for (User user : User.findAll()) {
        user.defaultLanguage = english;
        user.save();
      }

      for (Experiment experiment : Experiment.findAll()) {
        experiment.languages.add(english);
        experiment.uid = UUID.randomUUID().toString();

        try {
          File experimentDirectory = new File(Play.application().path().toString() + "/experiments/" + experiment.name + "_v2.2");
          FileUtils.writeStringToFile(new File(experimentDirectory, "client.html"), experiment.clientHtml);
          FileUtils.writeStringToFile(new File(experimentDirectory, "client-graph.js"), experiment.clientGraph);
          experiment.clientGraph = Experiment.defaultClientGraph();
          experiment.clientHtml = Experiment.defaultClientHTML();
        } catch (IOException ioe) {
          Logger.error("Error backing up v2.2 client.html and client.js to experiments directory");
        }

        experiment.save();
      }

      for (Content content : Content.findAll()) {
        Translation translation = new Translation();
        translation.language = english;
        translation.setHtml(content.html);
        content.translations.add(translation);
        content.save();
      }

      // TODO: Add message telling user what was done
      // TODO: Load v2.3 version notes as message from file system

    }

    //InitialData.insert(app);
    boolean isWin = System.getProperty("os.name").toUpperCase().indexOf("WIN") >= 0;
    String cwd = System.getProperty("user.dir");
    String mode = play.Play.application().configuration().getString("application.mode");
    if(mode.toUpperCase().equals("DEV")) {
      // Try to start the assets server
      try {
        ProcessBuilder pb = new ProcessBuilder("node", cwd + "/frontend/webpack/webpack.server.js");
        pb.directory(new File(cwd + "/frontend"));
        pb.inheritIO();
        process = pb.start();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    // TODO: We could build the production resources when the framework starts instead of having to build them manually
  }

  @Override
  public void onStop(Application app){
    if(process != null){
      process.destroy();
    }
  }

  @Override
  public Action onRequest(Http.Request request, Method actionMethod) {
    return super.onRequest(request, actionMethod);
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