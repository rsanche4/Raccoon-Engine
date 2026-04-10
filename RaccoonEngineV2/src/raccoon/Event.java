package raccoon;

public class Event implements Comparable<Event> {
	
	String script_name;
    int priority;

    public Event(String script_name, int priority) {
        this.script_name = script_name;
        this.priority = priority;
    }

    @Override
    public int compareTo(Event other) {
        return Integer.compare(this.priority, other.priority);
    }
}
