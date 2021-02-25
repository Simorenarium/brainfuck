/*
 *
 * Erstellt am: 16 Dec 2019 19:43:16
 * Erstellt von: Jonas Michel
 */
package brainfuck;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jonas Michel
 *
 */
public class Brainfuck {

	static class InstructionResult {
		private final Instruction next;
		private final long memoryPointer;

		public InstructionResult(	Instruction next,
									long memoryPointer) {
			this.next = next;
			this.memoryPointer = memoryPointer;
		}

	}

	static abstract class Instruction {
		private Instruction previousInstruction;
		private Instruction nextPlannedInstruction;

		public Instruction(	Instruction previousInstruction,
							Instruction nextPlannedInstruction) {
			this.previousInstruction = previousInstruction;
			this.nextPlannedInstruction = nextPlannedInstruction;

			if (previousInstruction != null && previousInstruction.nextPlannedInstruction == null)
				previousInstruction.nextPlannedInstruction = this;
		}

		InstructionResult apply(@SuppressWarnings("unused") Map<Long, Byte> memory, long memoryPointer) {
			return new InstructionResult(nextPlannedInstruction, memoryPointer);
		}
	}

	static class MemoryModification extends Instruction {
		byte difference;

		public MemoryModification(	final Instruction previousInstruction,
									final int difference) {
			super(previousInstruction, null);
			this.difference = (byte) difference;
		}

		@Override
		InstructionResult apply(Map<Long, Byte> memory, long memoryPointer) {
			memory.compute(memoryPointer, (unused, val) -> val == null ? apply((byte) 0) : apply(val));
			return super.apply(memory, memoryPointer);
		}

		byte apply(byte memVal) {
			return (byte) (memVal + difference);
		}
	}

	static class PointerModification extends Instruction {
		long difference;

		public PointerModification(	final Instruction previousInstruction,
									final long difference) {
			super(previousInstruction, null);
			this.difference = difference;
		}

		@Override
		InstructionResult apply(Map<Long, Byte> memory, long memoryPointer) {
			return super.apply(memory, memoryPointer + difference);
		}
	}

	static class InputToMemory extends Instruction {

		public InputToMemory(final Instruction previousInstruction) {
			super(previousInstruction, null);
		}

		@Override
		InstructionResult apply(Map<Long, Byte> memory, long memoryPointer) {
			memory.put(memoryPointer, apply());
			return super.apply(memory, memoryPointer);
		}

		byte apply() {
			try {
				return (byte) System.in.read();
			} catch (IOException e) {
				return -1;
			}
		}
	}

	static class OutputFromMemory extends Instruction {

		public OutputFromMemory(final Instruction previousInstruction) {
			super(previousInstruction, null);
		}

		@Override
		InstructionResult apply(Map<Long, Byte> memory, long memoryPointer) {
			System.out.write(memory.get(memoryPointer));
			return super.apply(memory, memoryPointer);
		}
	}

	static class LoopStart extends Instruction {
		private Instruction end;

		public LoopStart(final Instruction previousInstruction) {
			super(previousInstruction, null);
		}

		@Override
		InstructionResult apply(Map<Long, Byte> memory, long memoryPointer) {
			if (memory.get(memoryPointer) == 0)
				return new InstructionResult(end, memoryPointer);
			else
				return super.apply(memory, memoryPointer);
		}
	}

	static class LoopEnd extends Instruction {
		private Instruction start;

		public LoopEnd(final Instruction previousInstruction) {
			super(previousInstruction, null);
			Instruction iter = previousInstruction;
			while (!(iter instanceof LoopStart) && !(iter instanceof StartInstruction))
				iter = iter.previousInstruction;
			if (iter instanceof StartInstruction)
				throw new RuntimeException("need to descripe the error loopEnd Error");
			start = iter;
			((LoopStart) iter).end = this;
		}

		@Override
		InstructionResult apply(Map<Long, Byte> memory, long memoryPointer) {
			if (memory.get(memoryPointer) != 0)
				return new InstructionResult(start, memoryPointer);
			else
				return super.apply(memory, memoryPointer);
		}
	}

	static class StartInstruction extends Instruction {

		public StartInstruction() {
			super(null, null);
		}

	}

	private static final byte MOVE_RIGHT;
	private static final byte MOVE_LEFT;
	private static final byte MEM_INCREMENT;
	private static final byte MEM_DECREMENT;
	private static final byte OUT;
	private static final byte IN;
	private static final byte LOOP_START;
	private static final byte LOOP_END;

	static {
		try {
			MOVE_RIGHT = ">".getBytes("ASCII")[0];
			MOVE_LEFT = "<".getBytes("ASCII")[0];
			MEM_INCREMENT = "+".getBytes("ASCII")[0];
			MEM_DECREMENT = "-".getBytes("ASCII")[0];
			OUT = ".".getBytes("ASCII")[0];
			IN = ",".getBytes("ASCII")[0];
			LOOP_START = "[".getBytes("ASCII")[0];
			LOOP_END = "]".getBytes("ASCII")[0];
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) throws IOException {
		File file = new File(args[0]);
		FileReader reader = new FileReader(file, Charset.forName("ASCII"));
		ByteBuffer buf = ByteBuffer.allocate((int) (file.length()));
		byte r = -1;
		while ((r = (byte) reader.read()) != -1)
			buf.put(r);
		reader.close();

		Instruction instruction = compile(buf.array());
		execute(instruction);
	}

	private static void execute(Instruction instruction) {
		Instruction currentInstruction = instruction;
		Map<Long, Byte> memory = new HashMap<>();
		long memoryPointer = 0;
		System.out.println("Starting Execution...\n\n\n");
		while (currentInstruction != null) {
			memory.putIfAbsent(memoryPointer, (byte) 0);
			InstructionResult result = currentInstruction.apply(memory, memoryPointer);
			currentInstruction = result.next;
			memoryPointer = result.memoryPointer;
		}
		System.out.println("\n\n\nCompleted execution.");
	}

	private static Instruction compile(byte[] instructions) {
		System.out.println("Started compilation.");
		int totalLength = instructions.length;
		int steps = totalLength / 100;

		Instruction start = new StartInstruction();
		Instruction currentCompiledInstruction = start;
		for (int instructionPointer = 0; instructionPointer < instructions.length; instructionPointer++) {
			if (instructionPointer % steps == 0)
				System.out.println((instructionPointer + 1) + " / " + totalLength);

			currentCompiledInstruction = compile(currentCompiledInstruction, instructions[instructionPointer]);
		}
		System.out.println("Compilation successful");
		return start;
	}

	static Instruction compile(Instruction lastCompiledInstruction, byte currentInstruction) {
		if (MEM_INCREMENT == currentInstruction) {
			if (lastCompiledInstruction instanceof MemoryModification)
				((MemoryModification) lastCompiledInstruction).difference++;
			else
				return new MemoryModification(lastCompiledInstruction, 1);
		} else if (MEM_DECREMENT == currentInstruction) {
			if (lastCompiledInstruction instanceof MemoryModification)
				((MemoryModification) lastCompiledInstruction).difference--;
			else
				return new MemoryModification(lastCompiledInstruction, -1);
		} else if (MOVE_RIGHT == currentInstruction) {
			if (lastCompiledInstruction instanceof PointerModification)
				((PointerModification) lastCompiledInstruction).difference++;
			else
				return new PointerModification(lastCompiledInstruction, 1);
		} else if (MOVE_LEFT == currentInstruction) {
			if (lastCompiledInstruction instanceof PointerModification)
				((PointerModification) lastCompiledInstruction).difference--;
			else
				return new PointerModification(lastCompiledInstruction, -1);
		} else if (OUT == currentInstruction) {
			return new OutputFromMemory(lastCompiledInstruction);
		} else if (IN == currentInstruction) {
			return new InputToMemory(lastCompiledInstruction);
		} else if (LOOP_START == currentInstruction) {
			return new LoopStart(lastCompiledInstruction);
		} else if (LOOP_END == currentInstruction) {
			return new LoopEnd(lastCompiledInstruction);
		}
		return lastCompiledInstruction;
	}

}
