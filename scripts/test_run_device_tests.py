"""Pure parsing regressions for the observed Android run-as ls column output. No ADB."""
import unittest

from run_device_tests import parse_directory_listing


class DirectoryListingTests(unittest.TestCase):
    def test_observed_column_output_cannot_mean_no_data(self):
        with self.assertRaises(RuntimeError):
            parse_directory_listing(b".  ..  cache  code_cache  files  shared_prefs\n")

    def test_one_entry_per_line_finds_existing_production_roots(self):
        self.assertEqual(
            parse_directory_listing(b".\r\n..\r\ncache\r\nfiles\r\nshared_prefs\r\n"),
            {"cache", "files", "shared_prefs"},
        )

    def test_only_explicit_dot_entries_prove_empty_directory(self):
        self.assertEqual(parse_directory_listing(b".\n..\n"), set())
        for raw in (b"", b"files\n", b"ls: Permission denied\n", b".\n..\nfiles shared_prefs\n",
                    b".\n..\nfiles\nfiles\n", b".\n..\n\x1b[0mfiles\n", b".\n..\n\xff\n"):
            with self.subTest(raw=raw), self.assertRaises(RuntimeError):
                parse_directory_listing(raw)


if __name__ == "__main__":
    unittest.main()
