package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import play.Logger;
import play.mvc.*;
import play.data.format.*;
import play.data.validation.*;
import play.libs.Json;
import play.db.ebean.*;

import java.util.*;

import javax.persistence.*;

import com.avaje.ebean.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Entity
@Table(name="images")
public class Image extends Model
{
	@Id
	public Long id;

	@Constraints.Required
	@Lob
	public byte[] file;

    @Lob
    public byte[] thumbFile;

	@Constraints.Required
	public String fileName;

    public String thumbFileName;

	@Constraints.Required
	public String contentType;

	//public String thumbnail;

    @JsonIgnore
    public static Model.Finder<Long, Image> find = new Model.Finder(Long.class, Image.class);

    public static Image findById(Long id)
    {
        return find.where().eq("id", id).findUnique();
    }

    public Image() {
    }

    public Image(Image image) {
        this.file = Arrays.copyOf(image.file, image.file.length);
        if (image.thumbFile != null) {
            this.thumbFile = Arrays.copyOf(image.thumbFile, image.thumbFile.length);
            this.thumbFileName = image.thumbFileName;
        }
        this.fileName = image.fileName;
        this.contentType = image.contentType;
    }

    public String toString()
	{
		return "Image(" + id.toString() + ")";
	}

    public ObjectNode toJson() {
        ObjectNode image = Json.newObject();

        image.put("id", id);
        image.put("fileName", fileName);

        //image.put("thumbnail", thumbnail);

        return image;
    }
}
