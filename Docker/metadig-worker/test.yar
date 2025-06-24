rule HugeMistake
{
    meta:
        description = "Detects a specific phrase"
        author = "Insane Cyber"
    strings:
        $a = "I've made a huge mistake"
    condition:
        $a
}