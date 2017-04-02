package Model;

import java.util.Calendar;

public class MyDate implements Comparable<MyDate> {
	
	private Calendar cal;
	
	public MyDate(int year, int month, int days){
		cal = Calendar.getInstance();
		cal.set(Calendar.YEAR, year);
		cal.set(Calendar.MONTH, month-1);
		cal.set(Calendar.DAY_OF_MONTH, days);
		
	}
	public MyDate(Calendar cal2) {
		cal = cal2;
	}
	public MyDate addDays(int days) {
		Calendar cal2 = Calendar.getInstance();
		cal2.setTimeInMillis(cal.getTimeInMillis());
		cal2.add(Calendar.DATE, days);
		return new MyDate(cal2);
	}
	
	public String toString() {
		return cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH)+1) + "-" + cal.get(Calendar.DATE);
	}
	
	@Override
	public int compareTo(MyDate o) {
		return cal.compareTo(o.cal);
	}
	
}
