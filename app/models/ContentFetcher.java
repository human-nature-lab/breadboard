package models;


import java.util.*;

public class ContentFetcher
{
    public Experiment selectedExperiment;

    public ContentFetcher(Experiment selectedExperiment)
    {
        this.selectedExperiment = selectedExperiment;
    }

	public String get(String name)
	{
		//Content c = Content.findByName(name);
		Content c = selectedExperiment.getContentByName(name);
		if (c == null)
			return " ";
		return c.html;
	}

	public String get(String name, Object... parameters)
	{
		String returnContent = this.get(name);
		
		for (int i = 0; i < parameters.length; i++)
		{
			returnContent = returnContent.replace("{" + i + "}", parameters[i].toString());
		}

		return returnContent;
	}
}
