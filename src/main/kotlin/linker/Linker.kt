package venus.linker

import venus.riscv.Instruction
import venus.riscv.InstructionField
import venus.riscv.MemorySegments
import venus.riscv.Program
import venus.assembler.AssemblerError

data class RelocationInfo(val label: String, val offset: Int)

object Linker {
    fun link(progs: List<Program>): Program {
        val linkedProgram = Program()
        val globalTable = HashMap<String, Int>()
        val toRelocate = ArrayList<RelocationInfo>()
        var textTotalOffset = 0
        var dataTotalOffset = 0

        for (prog in progs) {
            for ((label, offset) in prog.labels) {
                val start = if (offset >= MemorySegments.STATIC_BEGIN) {
                    dataTotalOffset
                } else {
                    textTotalOffset
                }

                if (isGlobalLabel(label)) {
                    globalTable.put(label, start + offset)
                }
            }

            for ((label, offset) in prog.relocationTable) {
                toRelocate.add(RelocationInfo(label, textTotalOffset + offset))
            }

            for (inst in prog.insts) {
                linkedProgram.add(inst)
            }

            linkedProgram.addToData(prog.dataSegment)

            textTotalOffset += prog.textSize
            dataTotalOffset += prog.dataSize
        }

        for ((label, offset) in toRelocate) {
            /* FIXME: variable instruction sizes WILL break this */
            val inst = linkedProgram.insts[offset / 4]
            val toAddress = globalTable.get(label)
            if (toAddress == null) {
                throw AssemblerError("jump to invalid label ${label}")
            }

            /* TODO: allow for relocating things other than jumps */
            fillInJump(inst, offset, toAddress)
        }

        return linkedProgram
    }

    private fun fillInJump(inst: Instruction, currentAddress: Int, toAddress: Int) {
        /* TODO: error if jump too far */
        val imm = (toAddress - currentAddress) shr 1
        val imm_20 = imm shr 20
        val imm_10_1 = imm shr 1
        val imm_19_12 = imm shr 12
        val imm_11 = imm shr 20
        inst.setField(InstructionField.IMM_20, imm_20)
        inst.setField(InstructionField.IMM_10_1, imm_10_1)
        inst.setField(InstructionField.IMM_19_12, imm_19_12)
        inst.setField(InstructionField.IMM_11_J, imm_11)
    }

    /* TODO: add a real .globl assembler directive */
    private fun isGlobalLabel(lbl: String) = !lbl.startsWith("_")
}