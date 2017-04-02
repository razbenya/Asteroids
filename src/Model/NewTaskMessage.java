package Model;

public class NewTaskMessage implements Message {
	
	private final String tag = "task";
	private int id;
	private String nasaURL;

	public NewTaskMessage(){}
	
	public NewTaskMessage(int id, String url) {
		this.id = id;
		this.nasaURL = url;
	}

	
	public int getID() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	@Override
	public String getMessageString() {
		return tag+"@"+id+"@"+nasaURL;
	}

	@Override
	public void stringToMessage(String message) {
		String[] arr = message.split("@");
		this.id = Integer.parseInt(arr[1]);
		this.nasaURL = arr[2];
	}

	public String getUrl() {
		return nasaURL;
	}

	public void setUrl(String url) {
		this.nasaURL = url;
	}

	public String getTag() {
		return tag;
	}

}
