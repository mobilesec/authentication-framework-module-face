package at.usmile.panshot;

public class User {

	// ================================================================================================================
	// MEMBERS

	private String id;
	private String name;

	// ================================================================================================================
	// METHODS

	public User(String _id, String _name) {
		super();
		id = _id;
		name = _name;
	}

	@Override
	public String toString() {
		return "User [id=" + id + ", name=" + name + "]";
	}

	/**
	 * @return folder name string representing this user in the form "name_id"
	 */
	public String getFoldername() {
		return getName() + "_" + getId();
	}

	public String getId() {
		return id;
	}

	public void setId(String _id) {
		id = _id;
	}

	public String getName() {
		return name;
	}

	public void setName(String _name) {
		name = _name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		User other = (User) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
}
