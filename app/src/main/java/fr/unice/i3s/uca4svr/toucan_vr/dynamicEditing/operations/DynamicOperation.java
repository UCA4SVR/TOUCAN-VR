package fr.unice.i3s.uca4svr.toucan_vr.dynamicEditing.operations;

public abstract class DynamicOperation {
  private int milliseconds;
  private boolean millisecondsFlag;

  public DynamicOperation() {
    this.millisecondsFlag = false;
  }

  public int getMilliseconds() {
    return this.milliseconds;
  }

  public long getMicroseconds() {
    return this.milliseconds*1000;
  }

	public void setMilliseconds(int milliseconds) {
		this.milliseconds = milliseconds;
		this.millisecondsFlag = true;
	}

	public boolean isOK() {
    return this.millisecondsFlag;
  }


}
