package Model;

public class ManagerTerminateMessage implements Message{

	private final String tag = "terminate";
	private String url;
	
	
	public void stringToMessage(String terminateMessage) {
		String[] message = terminateMessage.split("@");
		
		url = message[1];
		
	}
	
	public ManagerTerminateMessage(){
		url = "";
	}
	 public ManagerTerminateMessage(String url) {
		 
		 
		 this.url = url;
		 
	 }
	
	
	
	
	@Override
	public String getMessageString() {
		return tag + "@" + url;
	}
	
	

}
