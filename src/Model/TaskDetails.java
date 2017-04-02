package Model;


public class TaskDetails {
	
	private double speed_threshold;
	private double diameter_threshold;
	private double miss_threshold;
	private MyDate start_date;
	private MyDate end_date;
	public TaskDetails(MyDate start_date,MyDate end_date, double speed_threshold, double diameter_threshold, 
			double miss_threshold ) {
		this.speed_threshold = speed_threshold;
		this.diameter_threshold = diameter_threshold;
		this.miss_threshold = miss_threshold;
		this.start_date = start_date;
		this.end_date = end_date;
	}
	public double getSpeed_threshold() {
		return speed_threshold;
	}
	public void setSpeed_threshold(double speed_threshold) {
		this.speed_threshold = speed_threshold;
	}
	public double getDiameter_threshold() {
		return diameter_threshold;
	}
	public void setDiameter_threshold(double diameter_threshold) {
		this.diameter_threshold = diameter_threshold;
	}
	public double getMiss_threshold() {
		return miss_threshold;
	}
	public void setMiss_threshold(double miss_threshold) {
		this.miss_threshold = miss_threshold;
	}
	public MyDate getStart_date() {
		return start_date;
	}
	public void setStart_date(MyDate start_date) {
		this.start_date = start_date;
	}
	public MyDate getEnd_date() {
		return end_date;
	}
	public void setEnd_date(MyDate end_date) {
		this.end_date = end_date;
	}
	
	
	

}
