package rew.lightgames.sudoku2

import org.junit.Assert.*
import org.junit.Test

class CellTest {

    @Test
    fun editable_cell_has_zero_original_number() {
        val cell = Cell(number = 5, original_number = 0)
        assertTrue("Cell with original_number=0 should be editable", cell.isEditable)
    }

    @Test
    fun given_cell_is_not_editable() {
        val cell = Cell(number = 5, original_number = 5)
        assertFalse("Cell with non-zero original_number should not be editable", cell.isEditable)
    }

    @Test
    fun isEditable_overwritten_by_init_block() {
        val cell = Cell(isEditable = true, number = 3, original_number = 7)
        assertFalse("isEditable should be overwritten to false when original_number != 0", cell.isEditable)
    }

    @Test
    fun isEditable_true_when_original_number_zero_regardless_of_param() {
        val cell = Cell(isEditable = false, number = 3, original_number = 0)
        assertTrue("isEditable should be overwritten to true when original_number == 0", cell.isEditable)
    }

    @Test
    fun addNote_toggles_note_on() {
        val cell = Cell(number = 0, original_number = 0)
        cell.addNote(5)
        assertTrue("Notes should contain 5 after adding", cell.notes.contains(5))
        assertEquals("Notes should have exactly 1 entry", 1, cell.notes.size)
    }

    @Test
    fun addNote_toggles_note_off() {
        val cell = Cell(number = 0, original_number = 0)
        cell.addNote(5)
        cell.addNote(5)
        assertFalse("Notes should not contain 5 after toggling off", cell.notes.contains(5))
        assertEquals("Notes should be empty after toggle off", 0, cell.notes.size)
    }

    @Test
    fun addNote_multiple_notes() {
        val cell = Cell(number = 0, original_number = 0)
        cell.addNote(1)
        cell.addNote(2)
        cell.addNote(3)
        assertEquals("Should have 3 notes", 3, cell.notes.size)
        assertTrue(cell.notes.containsAll(listOf(1, 2, 3)))
    }

    @Test
    fun clearNotes_empties_notes() {
        val cell = Cell(number = 0, original_number = 0)
        cell.addNote(1)
        cell.addNote(2)
        cell.clearNotes()
        assertTrue("Notes should be empty after clear", cell.notes.isEmpty())
    }

    @Test
    fun hint_cell_is_immutable() {
        val cell = Cell(number = 5, isHint = true, original_number = 0)
        assertTrue("Cell should be marked as hint", cell.isHint)
    }

    @Test
    fun data_class_equality() {
        val cell1 = Cell(number = 5, original_number = 5)
        val cell2 = Cell(number = 5, original_number = 5)
        assertEquals("Data classes with same values should be equal", cell1, cell2)
    }

    @Test
    fun data_class_inequality() {
        val cell1 = Cell(number = 5, original_number = 5)
        val cell2 = Cell(number = 3, original_number = 5)
        assertNotEquals("Data classes with different numbers should not be equal", cell1, cell2)
    }

    @Test
    fun default_values() {
        val cell = Cell(number = 0, original_number = 0)
        assertTrue("Default isEditable should be true", cell.isEditable)
        assertFalse("Default isHint should be false", cell.isHint)
        assertTrue("Default notes should be empty", cell.notes.isEmpty())
    }
}
