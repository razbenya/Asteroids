package Model;

public class Astroid implements Comparable<Astroid>{

	private final String GREEN ="#66ff66";
	private final String YELLOW = "#ffff4d";
	private final String RED = "#ff1a1a";
	private final String DEFAULT ="#ebebe0";
	private double speed;
	private double diameter_max;
	private double diameter_min;
	private boolean hazardous;
	private double missDistanceA;
	private double missDistanceK;
	private String name;
	private long id;
	private String color;
	private String close_approach_date;

	public Astroid(long id,String name,double speed,double diameter_max,double diameter_min,double missDistanceA,double missDistanceK,String close_approach_date,boolean hazardos){
		this.color = "";
		this.speed = speed;
		this.diameter_max=diameter_max;
		this.setDiameter_min(diameter_min);
		this.hazardous = hazardos;
		this.missDistanceA = missDistanceA ;
		this.setMissDistanceK(missDistanceK) ;
		this.name = name;
		this.id = id;
		this.close_approach_date = close_approach_date;
	}
	public String getColor(){
		return this.color;
	}
	public double getSpeed() {
		return speed;
	}
	public double getDiameter_max() {
		return diameter_max;
	}
	public void setDiameter_max(double diameter_max) {
		this.diameter_max = diameter_max;
	}
	public boolean isHazardous() {
		return hazardous;
	}
	public void setHazardous(boolean hazardous) {
		this.hazardous = hazardous;
	}
	public String getClose_approach_date() {
		return close_approach_date;
	}
	public void setClose_approach_date(String close_approach_date) {
		this.close_approach_date = close_approach_date;
	}
	public void setMissDistanceA(double missDistanceA) {
		this.missDistanceA = missDistanceA;
	}
	public void setSpeed(double speed) {
		this.speed = speed;
	}
	public double getDiameterMax() {
		return diameter_max;
	}
	public void setDiameterMax(double diameter) {
		this.diameter_max = diameter;
	}
	public boolean isHazardos() {
		return hazardous;
	}
	public void setHazardos(boolean hazardos) {
		this.hazardous = hazardos;
	}
	public double getMissDistanceA() {
		return missDistanceA;
	}
	public void setMissDistancea(double missDistanceA) {
		this.missDistanceA = missDistanceA;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}

	public void setColor(double sth,double dth,double mdth){
		if(this.hazardous){
			if(this.speed>=sth) {
				if(this.diameter_min >=dth){
					if(this.missDistanceA >= mdth)
						color = RED;
					else
						color = YELLOW;
				} else
					color = GREEN;		
			}
		} 
	}

	public void setColor(String color){
		this.color = color;
	}

	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("name:" + this.name+"\n");
		buffer.append("close_approach_date: "+this.getClose_approach_date()+"\n");
		buffer.append("kilometers_per_second:" + this.speed+"\n");
		buffer.append("estimated_diameter_min:" + this.diameter_min+"\n");
		buffer.append("estimated_diameter_max:" + this.diameter_max+"\n");
		buffer.append("miss_distance:" + this.missDistanceK+"\n\n\n\n");
		return buffer.toString();
	}

	public String toHtml(int count){
		StringBuffer buffer = new StringBuffer();
		if(color.equals("")) 
			this.color = DEFAULT;
		buffer.append("<tr align='center' bgcolor='"+this.color+"'>");
		buffer.append("<td> "+count+"</td>");
		buffer.append("<td> "+ this.name+"</td>");
		buffer.append("<td>" + this.getClose_approach_date()+"</font></td>");
		buffer.append("<td>" + this.speed+"</td>");
		buffer.append("<td>" + this.diameter_min+"</td>");
		buffer.append("<td>" + this.diameter_max+"</td>");
		buffer.append("<td>" + this.missDistanceK+"</td>");
		buffer.append("</tr>");
		return buffer.toString();
	}

	public double getMissDistanceK() {
		return missDistanceK;
	}

	public void setMissDistanceK(double missDistanceK) {
		this.missDistanceK = missDistanceK;
	}

	public double getDiameter_min() {
		return diameter_min;
	}

	public void setDiameter_min(double diameter_min) {
		this.diameter_min = diameter_min;
	}
	
	@Override
	public int compareTo(Astroid o) {
		int colorCompare = this.color.compareTo(o.getColor());
		if(this.color.equals(RED) && o.getColor().equals(YELLOW) || this.color.equals(YELLOW) && o.getColor().equals(RED))
			colorCompare = - colorCompare;
		if(colorCompare == 0){
			String[] s = this.close_approach_date.split("-");
			String[] s2 = o.getClose_approach_date().split("-");
			MyDate date1 = new MyDate(Integer.parseInt(s[0]),Integer.parseInt(s[1]),Integer.parseInt(s[2]));
			MyDate date2 = new MyDate(Integer.parseInt(s2[0]),Integer.parseInt(s2[1]),Integer.parseInt(s2[2]));
			return date1.compareTo(date2);
		}else 
			return -colorCompare;
	}
}