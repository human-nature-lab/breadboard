package exceptions;

import play.*;
import java.lang.Exception;

public class BreadboardException extends Exception
{
	public BreadboardException(String err)
	{
		super(err);
	}
}
