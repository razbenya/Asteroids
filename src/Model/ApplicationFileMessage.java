package Model;

public class ApplicationFileMessage implements Message{
	
	private final String tag = "inputFile";
	private String bucketName;
	private String fileName;
	private String url;
	private String n;
	private String d;
	

	public void stringToMessage(String fileMessage) {
		String[] message = fileMessage.split("@");
		bucketName = message[1];
		fileName = message[2];
		n = message[3];
		d = message[4];
		url = message[5];		
	}
	 public ApplicationFileMessage(String bucketName,String fileName, String url,String n, String d) {
		 
		 this.bucketName = bucketName;
		 this.fileName = fileName;
		 this.url = url;
		 this.n = n;
		 this.d = d;
		 
	 }
	 public String getN() {
		return n;
	}
	public void setN(String n) {
		this.n = n;
	}
	public String getD() {
		return d;
	}
	public void setD(String d) {
		this.d = d;
	}
	public ApplicationFileMessage(){}
	 
	 
	 
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
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public String getTag() {
		return tag;
	}
	@Override
	public String getMessageString() {
		return tag + "@" + bucketName + "@" + fileName + "@" + n + "@" + d + "@" + url;
	}
	 
	 
}
