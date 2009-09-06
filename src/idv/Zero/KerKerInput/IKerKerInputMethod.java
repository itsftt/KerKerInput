package idv.Zero.KerKerInput;

import android.inputmethodservice.Keyboard;

public abstract class IKerKerInputMethod {
	protected KerKerInputCore _core;
	
	/* Input Method Core */
	public void initInputMethod(KerKerInputCore core) { _core = core; }
	abstract public void onEnterInputMethod();
	public void onLostFocus()
	{
		_core.clearCandidates();
		_core.hideCandidatesView();
	}
	abstract public void destroyInputMethod();
	abstract public String getName();

	/* Keyboard Events */
	public Keyboard getDesiredKeyboard() { return null; }
	public boolean wantHandleEvent(int keyCode) { return (keyCode > -2); }
	abstract public boolean onKeyEvent(int keyCode, int[] keyCodes);
	abstract public void commitCandidate(int currentCandidate);
	public void onTextEvent(CharSequence text) { if (_core != null) _core.getConnection().commitText(text, 1); }
	
	/* Candidates Handling */
	abstract public void setTotalPages(int totalPages);
	abstract public void setCurrentPage(int currentPage);
}