using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace WellnessApp.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddDailyStepGoal : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "DailyStepGoal",
                table: "Users",
                type: "integer",
                nullable: false,
                defaultValue: 0);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "DailyStepGoal",
                table: "Users");
        }
    }
}
