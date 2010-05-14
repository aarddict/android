package aarddict;

/**
 * @author itkach
 */
public final class Entry {

    public String     title;
    public String     section;
    public long       articlePointer;
    public String 	  volumeId;

    public Entry(String volumeId, String title) {
        this(volumeId, title, -1);
    }

    public  Entry(String volumeId, String title, long articlePointer) {
        this.volumeId = volumeId;
        this.title = title == null ? "" : title;
        this.articlePointer = articlePointer;
    }


    @Override
    public String toString() {
        return title;
    }

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public long getArticlePointer() {
		return articlePointer;
	}

	public void setArticlePointer(long articlePointer) {
		this.articlePointer = articlePointer;
	}

	public String getVolumeId() {
		return volumeId;
	}

	public void setVolumeId(String volumeId) {
		this.volumeId = volumeId;
	}


}