# R8 full mode is the AGP default. Room, Hilt, kotlinx-serialization and Compose ship their own
# consumer rules, and NumLab uses no reflection of its own, so nothing extra is needed here.
# (exp4j and MPAndroidChart from PLAN §9 are not used; see docs/DECISIONS.md.)
