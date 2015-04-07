package at.usmile.panshot.genericobserver;

import java.util.Observable;
import java.util.Observer;

/**
 * A generic version of {@link Observer}.
 * 
 * @author Rainhard Findling
 * @date 02.03.2012
 * @version 1
 * @param <T>
 *            type of object trasported via {@link #update(Observable, Object)}.
 */
public interface GenericObserver<T> {
	/**
	 * This method is called whenever the observed object is changed. An
	 * application calls an <tt>Observable</tt> object's
	 * <code>notifyObservers</code> method to have all the object's observers
	 * notified of the change.
	 * 
	 * @param o
	 *            the observable object.
	 * @param arg
	 *            an argument passed to the <code>notifyObservers</code> method.
	 */
	void update(GenericObservable<T> o, T arg);
}
