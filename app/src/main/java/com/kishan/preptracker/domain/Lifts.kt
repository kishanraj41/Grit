package com.kishan.preptracker.domain

/**
 * Searchable lift library. v4 IDs wrap [Program.EX] so templates stay the source of names.
 * Extra lifts are Grit-authored (common gym names, text cues, no third-party media).
 */
data class Lift(
    val ex: Exercise,
    val primary: List<String>,
    val assist: List<String> = emptyList(),
    val equipment: String,
    val group: String,
    val cue: String,
) {
    val id: String get() = ex.id
    val name: String get() = ex.name
}

object Lifts {
    val GROUPS = listOf("chest", "back", "shoulders", "arms", "legs", "core", "cardio")
    val EQUIPMENT = listOf("barbell", "dumbbell", "cable", "machine", "bodyweight", "cardio")

    private fun w(id: String, pri: List<String>, sec: List<String>, eq: String, group: String, cue: String) =
        Lift(Program.EX.getValue(id), pri, sec, eq, group, cue)

    private fun x(
        id: String, name: String, lo: Int, hi: Int, rir: String, rest: Int, inc: Double,
        pri: List<String>, sec: List<String> = emptyList(), eq: String, group: String, cue: String,
        bw: Boolean = false,
    ) = Lift(Exercise(id, name, lo, hi, rir, rest, inc, bodyweight = bw), pri, sec, eq, group, cue)

    val ALL: List<Lift> = listOf(
        w("incbench", listOf("chest"), listOf("delts", "triceps"), "barbell", "chest", "Upper-chest press. Wrists stacked, pause on the chest."),
        w("cgb", listOf("chest", "triceps"), listOf("delts"), "barbell", "chest", "Narrow grip. Elbows in, lock out without bouncing."),
        w("fly", listOf("chest"), emptyList(), "cable", "chest", "Big stretch, slight elbow bend, squeeze at the midline."),
        w("dips", listOf("chest", "triceps"), listOf("delts"), "bodyweight", "chest", "Lean forward for chest. Added weight is extra, not total."),
        w("ohp", listOf("delts"), listOf("triceps", "traps"), "barbell", "shoulders", "Seated. Brace, press to lockout, do not shrug the finish."),
        w("cablat", listOf("delts"), listOf("traps"), "cable", "shoulders", "Lead with the elbow. Stop at shoulder height."),
        w("dblat", listOf("delts"), listOf("traps"), "dumbbell", "shoulders", "Lengthened partials on the last set."),
        w("reardelt", listOf("delts"), listOf("upper-back"), "cable", "shoulders", "Soft elbows. Pull the cables apart, not back."),
        w("pullup", listOf("upper-back"), listOf("biceps", "forearms"), "bodyweight", "back", "Dead hang, chest up. Logged load is added weight."),
        w("pulldown", listOf("upper-back"), listOf("biceps"), "cable", "back", "Full stretch at the top, elbows to hips."),
        w("csrow", listOf("upper-back"), listOf("biceps", "traps"), "machine", "back", "Chest glued. Pull to the lower ribs."),
        w("squat", listOf("quads", "glutes"), listOf("hams", "lower-back"), "barbell", "legs", "High-bar. Sit between the heels, knees out."),
        w("hack", listOf("quads"), listOf("glutes"), "machine", "legs", "Deep, controlled. Do not bounce out of the hole."),
        w("legpress", listOf("quads"), listOf("glutes"), "machine", "legs", "Deep range. Lower back stays on the pad."),
        w("rdl", listOf("hams", "glutes"), listOf("lower-back"), "barbell", "legs", "Soft knees, bar close, stretch the hams."),
        w("thrust", listOf("glutes"), listOf("hams"), "barbell", "legs", "Chin tucked. Pause at lockout."),
        w("seatcurl", listOf("hams"), emptyList(), "machine", "legs", "Hips down. Slow the lengthened half."),
        w("stcalf", listOf("calves"), emptyList(), "machine", "legs", "Two-second pause in the stretch."),
        w("seatcalf", listOf("calves"), emptyList(), "machine", "legs", "Soleus. Full stretch, no bounce."),
        w("hlr", listOf("abs"), listOf("hip-flexors"), "bodyweight", "core", "Posterior tilt. Legs raise, hips do not swing."),
        w("crunch", listOf("abs"), listOf("obliques"), "cable", "core", "Ribs to pelvis. Do not yank the rope."),
        w("preacher", listOf("biceps"), listOf("forearms"), "machine", "arms", "Pad in the armpit. Stretch at the bottom."),
        w("inccurl", listOf("biceps"), emptyList(), "dumbbell", "arms", "Elbows behind the torso. No swing."),
        w("bayes", listOf("biceps"), emptyList(), "cable", "arms", "Arm behind you. Curl across the stretch."),
        w("ezcurl", listOf("biceps"), listOf("forearms"), "barbell", "arms", "EZ bar. Elbows pinned, no torso rock."),
        w("hammer", listOf("biceps"), listOf("forearms"), "dumbbell", "arms", "Neutral grip. Wrist stays stacked."),
        w("ohext", listOf("triceps"), emptyList(), "cable", "arms", "Elbows high. Stretch at the bottom."),
        w("pushdown", listOf("triceps"), emptyList(), "cable", "arms", "Elbows glued. Spread the rope at the bottom."),

        x("bench", "Flat barbell bench", 5, 8, "2", 180, 2.5, listOf("chest"), listOf("delts", "triceps"), "barbell", "chest", "Feet down, pause on the chest, press back."),
        x("dbpress", "Dumbbell bench press", 8, 12, "1–2", 120, 2.5, listOf("chest"), listOf("delts", "triceps"), "dumbbell", "chest", "Dumbbells to the sides, not on the chest."),
        x("decline", "Decline bench press", 6, 10, "2", 150, 2.5, listOf("chest"), listOf("triceps"), "barbell", "chest", "Lower chest. Controlled touch."),
        x("pecdeck", "Pec deck", 10, 15, "0–1", 75, 2.5, listOf("chest"), emptyList(), "machine", "chest", "Shoulders packed. Squeeze, do not crash."),
        x("pushup", "Push-up", 8, 20, "1", 60, 0.0, listOf("chest"), listOf("triceps", "delts"), "bodyweight", "chest", "Body line. Chest to a fist-height floor.", bw = true),
        x("floorpress", "Floor press", 6, 10, "2", 150, 2.5, listOf("chest", "triceps"), listOf("delts"), "barbell", "chest", "Triceps lockout. Pause on the floor."),
        x("deadlift", "Conventional deadlift", 3, 6, "2", 210, 5.0, listOf("hams", "glutes", "upper-back"), listOf("lower-back", "forearms"), "barbell", "back", "Push the floor. Bar up the legs."),
        x("sldl", "Stiff-leg deadlift", 6, 10, "2", 150, 2.5, listOf("hams"), listOf("glutes", "lower-back"), "barbell", "legs", "Softer than an RDL at the knee. Stretch."),
        x("trapdead", "Trap-bar deadlift", 4, 8, "2", 180, 5.0, listOf("quads", "glutes"), listOf("upper-back"), "barbell", "legs", "High handles if you have them. Stand tall."),
        x("pendlay", "Pendlay row", 5, 8, "2", 150, 2.5, listOf("upper-back"), listOf("biceps", "lower-back"), "barbell", "back", "Dead stop each rep. Torso parallel."),
        x("tbar", "T-bar row", 8, 12, "1–2", 120, 5.0, listOf("upper-back"), listOf("biceps", "traps"), "barbell", "back", "Chest up. Pull to the stomach."),
        x("seatedrow", "Seated cable row", 8, 12, "1", 90, 2.5, listOf("upper-back"), listOf("biceps"), "cable", "back", "Do not rock. Shoulder blades first."),
        x("facepull", "Face pull", 12, 20, "0–1", 60, 1.0, listOf("delts"), listOf("upper-back"), "cable", "shoulders", "External rotation. Rope to the forehead."),
        x("shrug", "Barbell shrug", 8, 15, "1", 90, 5.0, listOf("traps"), listOf("forearms"), "barbell", "back", "Straight up. No roll."),
        x("pullover", "Dumbbell pullover", 10, 15, "1", 75, 2.5, listOf("upper-back", "chest"), listOf("serratus"), "dumbbell", "back", "Ribs down. Stretch at the top."),
        x("chinup", "Chin-up", 5, 10, "2", 150, 2.5, listOf("upper-back", "biceps"), listOf("forearms"), "bodyweight", "back", "Supinated. Added weight is extra.", bw = true),
        x("lunge", "Walking lunge", 8, 12, "1", 90, 2.5, listOf("quads", "glutes"), listOf("hams"), "dumbbell", "legs", "Long step. Knee tracks the toe."),
        x("split", "Bulgarian split squat", 8, 12, "1", 90, 2.5, listOf("quads", "glutes"), emptyList(), "dumbbell", "legs", "Torso tall. Front heel stays down."),
        x("legext", "Leg extension", 10, 15, "0–1", 75, 2.5, listOf("quads"), emptyList(), "machine", "legs", "Pause at the top. Hips glued."),
        x("stepup", "Dumbbell step-up", 8, 12, "1", 90, 2.5, listOf("quads", "glutes"), emptyList(), "dumbbell", "legs", "Drive through the front foot. No push-off."),
        x("ghr", "Glute-ham raise", 6, 10, "1", 120, 0.0, listOf("hams", "glutes"), listOf("lower-back"), "machine", "legs", "Hips stay extended. Curl with the hams."),
        x("nordic", "Nordic ham curl", 4, 8, "1", 120, 0.0, listOf("hams"), emptyList(), "bodyweight", "legs", "Slow the lowering. Hands catch if needed."),
        x("bridge", "Glute bridge", 10, 15, "1", 60, 5.0, listOf("glutes"), listOf("hams"), "barbell", "legs", "Posterior tilt. Squeeze at the top."),
        x("hipab", "Hip abduction", 12, 20, "0–1", 60, 2.5, listOf("glutes"), emptyList(), "machine", "legs", "Lean forward slightly. Glute med."),
        x("backext", "45-degree back extension", 10, 15, "1", 75, 2.5, listOf("lower-back", "glutes"), listOf("hams"), "machine", "back", "Neutral neck. Do not hyperextend."),
        x("abwheel", "Ab wheel rollout", 6, 12, "1", 75, 0.0, listOf("abs"), listOf("serratus"), "bodyweight", "core", "Ribs down. Only as far as you stay braced."),
        x("plank", "Plank", 20, 60, "1", 45, 0.0, listOf("abs"), listOf("obliques"), "bodyweight", "core", "Timed hold. Squeeze glutes, ribs in."),
        x("pallof", "Pallof press", 8, 12, "1", 60, 2.5, listOf("obliques"), listOf("abs"), "cable", "core", "Press out, resist rotation."),
        x("skull", "Skull crusher", 8, 12, "1", 75, 2.5, listOf("triceps"), emptyList(), "barbell", "arms", "Elbows fixed. Lower to the forehead."),
        x("kickback", "Triceps kickback", 12, 15, "0–1", 60, 1.0, listOf("triceps"), emptyList(), "dumbbell", "arms", "Upper arm glued. Squeeze the lockout."),
        x("concurl", "Concentration curl", 10, 15, "0–1", 60, 1.0, listOf("biceps"), emptyList(), "dumbbell", "arms", "Elbow on the inner thigh. No swing."),
        x("cablecurl", "Standing cable curl", 10, 15, "0–1", 60, 2.5, listOf("biceps"), listOf("forearms"), "cable", "arms", "Constant tension. Elbows at the sides."),
        x("wristcurl", "Wrist curl", 12, 20, "0–1", 45, 1.0, listOf("forearms"), emptyList(), "dumbbell", "arms", "Forearms on the bench. Full roll."),
        x("farmer", "Farmer carry", 20, 40, "1", 90, 5.0, listOf("forearms", "traps"), listOf("abs"), "dumbbell", "back", "Timed or stepped. Tall walk, no lean."),
        x("machineohp", "Machine shoulder press", 8, 12, "1", 120, 2.5, listOf("delts"), listOf("triceps"), "machine", "shoulders", "Do not crash the stops."),
        x("upright", "Upright row", 10, 15, "1", 75, 2.5, listOf("delts", "traps"), emptyList(), "barbell", "shoulders", "Elbows lead. Stop at chest height."),
        x("walk", "Incline walk", 20, 40, "1", 0, 0.0, listOf("calves"), listOf("glutes"), "cardio", "cardio", "Timed. Easy talk pace unless noted."),
        x("bike", "Stationary bike", 15, 30, "1", 0, 0.0, listOf("quads"), listOf("calves"), "cardio", "cardio", "Timed. Log minutes and kph on the set."),
        x("rower", "Row erg", 10, 20, "1", 0, 0.0, listOf("upper-back"), listOf("hams", "quads"), "cardio", "cardio", "Legs, then hip, then arms."),
        x("run", "Run", 10, 30, "1", 0, 0.0, listOf("quads", "calves"), listOf("hams"), "cardio", "cardio", "Timed. Easy unless the note says intervals."),
        x("stair", "Stair climber", 10, 20, "1", 0, 0.0, listOf("glutes", "quads"), listOf("calves"), "cardio", "cardio", "Upright. Do not hang on the rails."),
    )

    val BY_ID: Map<String, Lift> = ALL.associateBy { it.id }

    fun search(query: String, group: String?, equipment: String?): List<Lift> {
        val q = query.trim().lowercase()
        return ALL.filter { lift ->
            (group == null || lift.group == group) &&
                (equipment == null || lift.equipment == equipment) &&
                (q.isEmpty() || lift.name.lowercase().contains(q) || lift.group.contains(q) || lift.equipment.contains(q) || lift.ex.id.contains(q))
        }
    }
}
