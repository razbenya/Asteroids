package Model;

public class CompleteTaskMessage implements Message{
	
	public CompleteTaskMessage() {
	}

	private final String tag = "complete";
	private int id;
	private String jsonString;
	private String startDate;

	public CompleteTaskMessage(int id, String jsonString,String startDate) {
		super();
		this.id = id;
		this.jsonString = jsonString;
		this.startDate = startDate;
	}

	public String getStartDate() {
		return startDate;
	}

	public void setStartDate(String startDate) {
		this.startDate = startDate;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getJsonString() {
		return jsonString;
	}

	public void setJsonString(String jsonString) {
		this.jsonString = jsonString;
	}

	public String getTag() {
		return tag;
	}

	@Override
	public String getMessageString() {
		return this.tag+"@"+this.id+"@"+this.jsonString+ "@"+this.startDate;	
	}

	@Override
	public void stringToMessage(String message) {
		String[] arr = message.split("@");
		this.id = Integer.parseInt(arr[1]);
		this.jsonString = arr[2];
		this.startDate = arr[3];
	}

	

	

}
