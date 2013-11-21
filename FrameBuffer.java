/**
 * @author      Y6332150
 * @version     1.0
 * @since       2013-20-11
 * 
 * A circular buffer for frames
 * drops older frames as new overwrite them
 * 
 * TODO: In case there is a need for a more generic stack, 
 * refactor this to use generics
 *
 */
 
package embs;
import embs.Frame;

public class FrameBuffer {
	private int size;
	private int start;
	private int end;
	private int mirror;
	private Frame[] buffer;
	
	/**
     * @param size	size of the buffer
     */
	public FrameBuffer(int size) {
		this.size = size;
		this.buffer = new Frame[size];
		this.start = 0;
		this.end = 0;
		this.mirror = 0;
	}
	
	/*
	 * @return a frame from the buffer, decrements the count
	 */
	public Frame pull() {
		if (end == start)
			return null;
		
		Frame value = buffer[start % size];
		start++;
		
		// Use the chance to reset the start/end if they end up on the same index
		if (start == end) {
			start = start % size;
			end = end % size;
		}
		
		return value;
	}
	
	/*
	 * @param frame		frame to push to the buffer
	 */
	public void push(Frame frame) {
		buffer[end % size] = frame;
		end++;
		
		// Check if we need to move the start (so that older frames are pulled first)
		if (end % size == start % size) {
			start++;
		}
	}
	
	/*
	 * @return true if buffer is empty
	 */
	public boolean isEmpty() {
		return start == end;
	}
	
	/*
	 * @return number of items in the buffer
	 */
	public int count() {
		return (end - start) % size;
	}
}
