package Model;

public class SummaryMessage implements Message{
	
	private final String tag = "summary";
	private String bucketName;
	private String fileName;
	
	public SummaryMessage(String bucketName, String fileName) {
		this.bucketName = bucketName;
		this.fileName = fileName;
		
	}
	public SummaryMessage() {
		this.bucketName = "";
		this.fileName = "";
	}
	public String getBucketName() {
		return bucketName;
	}
	public void setBucketName(String bucketName) {
		this.bucketName = bucketName;
	}
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	public String getTag() {
		return tag;
	}
	@Override
	public String getMessageString() {
		// TODO Auto-generated method stub
		return tag + "@" + fileName + "@" + bucketName;
	}
	@Override
	public void stringToMessage(String fileMessage) {
		String[] message = fileMessage.split("@");
		fileName = message[1];
		bucketName = message[2];
	}

}
