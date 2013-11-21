/**
 * @author      Y6332150
 * @version     1.0
 * @since       2013-20-11
 * 
 * A simple data structure to represent
 * a stack - as the Java Stack is not available
 * 
 */

package embs;
import embs.Session;

public class SessionStack {
	/**
	 * Size of the current stack we have
	 */
	private int size;
	
	/**
	 * Current index in the stack
	 */
	private int index;
	
	/**
	 * The stack itself, represented as an array
	 */
	private Session[] stack;
	
	/**
	 * @param initialSize	initial size for the stack, the stack will grow when needed
	 */
	public SessionStack(int initialSize) {
		this.size = initialSize;
		this.index = 0;
		this.stack = new Session[size];
	}
	
	/**
	 * Look at the top of the stack without modifying it
	 * @return top Session from the stack
	 */
	public Session peek() {
		if (index >= 0)
			return stack[index];
		
		return null;
	}
	
	/**
	 * Remove the top session from the stack
	 * @return top Session that was just removed from the stack
	 */
	public Session pop() {
		int oldIndex = index;
		if (index >= 0) {
			index--;
			return stack[oldIndex];
		}
		
		return null;
	}
	
	/**
	 * Push a new session to the stack
	 * @param session	Session to add to the stack
	 * @return the session that was just pushed to the stack
	 */
	public Session push(Session session) {
		index++;
		
		if (index == size) {
			int newSize = size + 5;
			Session[] expandedStack = new Session[newSize];
			for (int i = 0; i < size; i++) {
				expandedStack[i] = stack[i];
			}
			
			size = newSize;
			stack = expandedStack;
		}
		
		stack[index] = session;
		return session;
	}
	
	/**
	 * @return true if the stack is considered empty
	 */
	public boolean isEmpty() {
		return index < 0;
	}
}
