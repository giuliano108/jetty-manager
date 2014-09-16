package bz._108;

class NoJVMsFound extends Exception {
    public NoJVMsFound() { super(); }
    public NoJVMsFound(String message) { super(message); }
    public NoJVMsFound(String message, Throwable cause) { super(message, cause); }
    public NoJVMsFound(Throwable cause) { super(cause); }
}

class CannotAttachToJVM extends Exception {
    public CannotAttachToJVM() { super(); }
    public CannotAttachToJVM(String message) { super(message); }
    public CannotAttachToJVM(String message, Throwable cause) { super(message, cause); }
    public CannotAttachToJVM(Throwable cause) { super(cause); }
}
