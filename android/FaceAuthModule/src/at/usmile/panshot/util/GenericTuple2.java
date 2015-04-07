package at.usmile.panshot.util;

/**
 * Holds 2 values of different type that should stick together. Perfect for returning a tuple of values which are of different
 * type.
 * 
 * (being sick of creating classes just for handling multiple return values of different type - Java needs more functional
 * programming).
 * 
 * @author Rainhard Findling
 * @date 10 Jan 2014
 * @version 1
 * @param <T1>
 * @param <T2>
 */
public class GenericTuple2<T1, T2> {

	// ================================================================================================================
	// MEMBERS

	public T1 value1;
	public T2 value2;

	// ================================================================================================================
	// METHODS

	public GenericTuple2(T1 _value1, T2 _value2) {
		value1 = _value1;
		value2 = _value2;
	}

	@Override
	public String toString() {
		return "GenericTuple2 [value1=" + value1 + ", value2=" + value2 + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value1 == null) ? 0 : value1.hashCode());
		result = prime * result + ((value2 == null) ? 0 : value2.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		GenericTuple2 other = (GenericTuple2) obj;
		if (value1 == null) {
			if (other.value1 != null)
				return false;
		} else if (!value1.equals(other.value1))
			return false;
		if (value2 == null) {
			if (other.value2 != null)
				return false;
		} else if (!value2.equals(other.value2))
			return false;
		return true;
	}

}
