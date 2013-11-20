//
//	A simple data structure to represent
//	a stack - as the Java Stack is not available
//
//	As it is a specific usecase, there is no need to
//	bother with generics for the time being
//

package embs;

import embs.Session;

public class SessionStack {
	private int size;
	private int index;
	private Session[] stack;
	
	public SessionStack(int initialSize) {
		this.size = initialSize;
		this.index = 0;
		this.stack = new Session[size];
	}
	
	public Session peek() {
		if (index >= 0)
			return stack[index];
		
		return null;
	}
	
	public Session pop() {
		int oldIndex = index;
		if (index >= 0) {
			index--;
			return stack[oldIndex];
		}
		
		return null;
	}
	
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
	
	public boolean isEmpty() {
		return index < 0;
	}
}
